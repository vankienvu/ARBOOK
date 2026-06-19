package com.arbook.backend;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.arbook.backend.payment.util.VnpayUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@AutoConfigureMockMvc
public class VnpayIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testHmacSHA512() {
        String key = "KOUWNAMC7XZG2KHS3PVNK5E5AM2O10WP";
        String data = "vnp_Amount=19000000&vnp_Command=pay&vnp_CreateDate=20260618195800&vnp_CurrCode=VND&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=ARBook+Premium&vnp_OrderType=other&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A8086%2Fpayment%2Freturn&vnp_TmnCode=1DFCR9HR&vnp_TxnRef=test_txn_123&vnp_Version=2.1.0";
        String hash = VnpayUtil.hmacSHA512(key, data);
        assertNotNull(hash);
        assertEquals(128, hash.length()); // SHA-512 hex is 128 characters long
    }

    @Test
    public void testHashAllFields() {
        String key = "KOUWNAMC7XZG2KHS3PVNK5E5AM2O10WP";
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", "1DFCR9HR");
        fields.put("vnp_Amount", "19000000");
        
        String hash = VnpayUtil.hashAllFields(fields, key);
        assertNotNull(hash);
    }

    @Test
    public void testCheckoutUnauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/payment/checkout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    public void testIpnInvalidChecksum() throws Exception {
        mockMvc.perform(get("/api/payment/ipn")
                .param("vnp_TxnRef", "test_txn")
                .param("vnp_Amount", "19000000")
                .param("vnp_ResponseCode", "00")
                .param("vnp_SecureHash", "invalid_hash_value")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"))
                .andExpect(jsonPath("$.Message").value("Invalid Checksum"));
    }

    @Test
    public void testReturnInvalidChecksum() throws Exception {
        mockMvc.perform(get("/payment/return")
                .param("vnp_TxnRef", "test_txn")
                .param("vnp_Amount", "19000000")
                .param("vnp_ResponseCode", "00")
                .param("vnp_SecureHash", "invalid_hash_value"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String viewName = result.getModelAndView().getViewName();
                    assertEquals("payment/fail", viewName);
                });
    }
}
