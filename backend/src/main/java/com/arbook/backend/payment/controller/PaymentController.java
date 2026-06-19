package com.arbook.backend.payment.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.arbook.backend.payment.util.VnpayUtil;
import com.arbook.backend.security.SecurityFacade;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class PaymentController {

    private final JdbcTemplate jdbc;
    private final SecurityFacade security;

    @Value("${app.vnpay.tmn-code}")
    private String tmnCode;

    @Value("${app.vnpay.hash-secret}")
    private String hashSecret;

    @Value("${app.vnpay.url}")
    private String vnpayUrl;

    @Value("${app.backend-base-url}")
    private String backendBaseUrl;

    public PaymentController(JdbcTemplate jdbc, SecurityFacade security) {
        this.jdbc = jdbc;
        this.security = security;
    }

    @GetMapping("/payment/checkout")
    public String checkout(HttpServletRequest request) {
        Long currentUserId = security.currentUserIdOrNull();
        if (currentUserId == null) {
            return "redirect:/login";
        }

        String txnRef = UUID.randomUUID().toString();
        long amountInVnd = 190000L; // 190,000 VND
        long amountInCents = amountInVnd * 100L;

        // Record the transaction as PENDING in database
        jdbc.update("""
            insert into payments(user_id, txn_ref, amount, description, status, created_at, updated_at)
            values (?, ?, ?, ?, 'PENDING', now(), now())
            """, currentUserId, txnRef, amountInVnd, "Đăng ký gói Teacher Premium");

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", tmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amountInCents));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", txnRef);
        vnp_Params.put("vnp_OrderInfo", "ARBook Premium - Nguoi dung ID " + currentUserId);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", backendBaseUrl + "/payment/return");
        vnp_Params.put("vnp_IpAddr", VnpayUtil.getIpAddress(request));

        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("GMT+7"));
        String vnp_CreateDate = formatter.format(new java.util.Date());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        // Sort parameters to build checksum and query string
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        try {
            for (java.util.Iterator<String> itr = fieldNames.iterator(); itr.hasNext();) {
                String fieldName = itr.next();
                String fieldValue = vnp_Params.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    // Build Hash Data
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(VnpayUtil.encode(fieldValue));

                    // Build Query
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    if (itr.hasNext()) {
                        query.append('&');
                        hashData.append('&');
                    }
                }
            }

            String queryUrl = query.toString();
            String vnp_SecureHash = VnpayUtil.hmacSHA512(hashSecret, hashData.toString());
            queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
            String paymentUrl = vnpayUrl + "?" + queryUrl;

            return "redirect:" + paymentUrl;

        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo URL thanh toán VNPAY", e);
        }
    }

    @GetMapping("/payment/return")
    public String paymentReturn(HttpServletRequest request, Model model) {
        Map<String, String> fields = new HashMap<>();
        for (java.util.Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                fields.put(fieldName, fieldValue);
            }
        }

        String vnp_SecureHash = request.getParameter("vnp_SecureHash");
        fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        String signValue = VnpayUtil.hashAllFields(fields, hashSecret);
        boolean isValidSignature = signValue.equalsIgnoreCase(vnp_SecureHash);

        if (isValidSignature) {
            String txnRef = request.getParameter("vnp_TxnRef");
            String responseCode = request.getParameter("vnp_ResponseCode");
            String vnpTxnNo = request.getParameter("vnp_TransactionNo");

            List<Map<String, Object>> paymentList = jdbc.queryForList("select * from payments where txn_ref = ?", txnRef);
            if (!paymentList.isEmpty()) {
                Map<String, Object> payment = paymentList.get(0);
                String currentStatus = (String) payment.get("status");
                Long userId = ((Number) payment.get("user_id")).longValue();

                if ("PENDING".equals(currentStatus)) {
                    if ("00".equals(responseCode)) {
                        // Update payment to SUCCESS
                        jdbc.update("""
                            update payments set status = 'SUCCESS', vnp_txn_no = ?, vnp_response_code = ?, updated_at = now()
                            where txn_ref = ?
                            """, vnpTxnNo, responseCode, txnRef);
                        // Upgrade user to Premium
                        jdbc.update("update users set is_premium = true where id = ?", userId);
                        model.addAttribute("success", true);
                    } else {
                        // Update payment to FAILED
                        jdbc.update("""
                            update payments set status = 'FAILED', vnp_txn_no = ?, vnp_response_code = ?, updated_at = now()
                            where txn_ref = ?
                            """, vnpTxnNo, responseCode, txnRef);
                        model.addAttribute("success", false);
                    }
                } else {
                    model.addAttribute("success", "SUCCESS".equals(currentStatus));
                }

                model.addAttribute("txnRef", txnRef);
                model.addAttribute("amount", payment.get("amount"));
                model.addAttribute("description", payment.get("description"));
                model.addAttribute("responseCode", responseCode);
            } else {
                model.addAttribute("success", false);
                model.addAttribute("error", "Không tìm thấy thông tin giao dịch trong cơ sở dữ liệu.");
            }
        } else {
            model.addAttribute("success", false);
            model.addAttribute("error", "Chữ ký xác thực giao dịch từ VNPAY không hợp lệ.");
        }

        Boolean isSuccess = (Boolean) model.getAttribute("success");
        if (isSuccess != null && isSuccess) {
            return "payment/success";
        } else {
            return "payment/fail";
        }
    }

    @GetMapping("/api/payment/ipn")
    @ResponseBody
    public Map<String, String> ipn(HttpServletRequest request) {
        Map<String, String> response = new HashMap<>();
        try {
            Map<String, String> fields = new HashMap<>();
            for (java.util.Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
                String fieldName = params.nextElement();
                String fieldValue = request.getParameter(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    fields.put(fieldName, fieldValue);
                }
            }

            String vnp_SecureHash = request.getParameter("vnp_SecureHash");
            fields.remove("vnp_SecureHash");
            fields.remove("vnp_SecureHashType");

            String signValue = VnpayUtil.hashAllFields(fields, hashSecret);

            if (!signValue.equalsIgnoreCase(vnp_SecureHash)) {
                response.put("RspCode", "97");
                response.put("Message", "Invalid Checksum");
                return response;
            }

            String txnRef = request.getParameter("vnp_TxnRef");
            String responseCode = request.getParameter("vnp_ResponseCode");
            String vnpTxnNo = request.getParameter("vnp_TransactionNo");
            String vnpAmountStr = request.getParameter("vnp_Amount");

            List<Map<String, Object>> paymentList = jdbc.queryForList("select * from payments where txn_ref = ?", txnRef);
            if (paymentList.isEmpty()) {
                response.put("RspCode", "01");
                response.put("Message", "Order not Found");
                return response;
            }

            Map<String, Object> payment = paymentList.get(0);
            long dbAmount = ((Number) payment.get("amount")).longValue();
            long vnpAmount = Long.parseLong(vnpAmountStr) / 100L;

            if (dbAmount != vnpAmount) {
                response.put("RspCode", "04");
                response.put("Message", "Invalid Amount");
                return response;
            }

            String currentStatus = (String) payment.get("status");
            if (!"PENDING".equals(currentStatus)) {
                response.put("RspCode", "02");
                response.put("Message", "Order already confirmed");
                return response;
            }

            Long userId = ((Number) payment.get("user_id")).longValue();
            if ("00".equals(responseCode)) {
                // Update payment to SUCCESS
                jdbc.update("""
                    update payments set status = 'SUCCESS', vnp_txn_no = ?, vnp_response_code = ?, updated_at = now()
                    where txn_ref = ?
                    """, vnpTxnNo, responseCode, txnRef);
                // Upgrade user to Premium
                jdbc.update("update users set is_premium = true where id = ?", userId);
            } else {
                // Update payment to FAILED
                jdbc.update("""
                    update payments set status = 'FAILED', vnp_txn_no = ?, vnp_response_code = ?, updated_at = now()
                    where txn_ref = ?
                    """, vnpTxnNo, responseCode, txnRef);
            }

            response.put("RspCode", "00");
            response.put("Message", "Confirm success");

        } catch (Exception e) {
            response.put("RspCode", "99");
            response.put("Message", "Unknown error");
        }
        return response;
    }
}
