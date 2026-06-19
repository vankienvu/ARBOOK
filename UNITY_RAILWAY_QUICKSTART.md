# Huong Dan Nhanh: Railway + Unity Vuforia

## 1. Railway PostgreSQL

File cau hinh local da duoc tao tai:

```text
D:\ARBOOK\backend\.env
```

File nay bi ignore boi Git, khong commit len source code.

Backend da duoc test voi Railway PostgreSQL va API marker demo da tra ve `200`.

Chay backend lai khi can:

```powershell
cd D:\ARBOOK\backend
.\mvnw.cmd spring-boot:run
```

Kiem tra nhanh:

```powershell
curl.exe http://localhost:8080/api/ar-markers/resolve?code=BIO_CELL_001
```

Mo web:

```text
http://localhost:8080
```

Tai khoan demo:

```text
admin@artextbook.com / 123456
content@artextbook.com / 123456
teacher@artextbook.com / 123456
student@artextbook.com / 123456
```

## 2. Cai Unity

1. Cai Unity Hub tu trang chinh thuc cua Unity.
2. Mo Unity Hub, dang nhap tai khoan Unity.
3. Vao `Installs` -> `Install Editor`.
4. Chon Unity `2022.3.62f1`. Neu Hub khong hien dung ban nay, chon mot ban `2022.3 LTS` gan nhat.
5. Module bat buoc cho demo local: khong can Android/iOS. Co the tick Visual Studio neu may chua co IDE C#.

## 3. Mo Unity Project

1. Unity Hub -> `Projects` -> `Add` -> `Add project from disk`.
2. Chon folder:

```text
D:\ARBOOK\UnityProject
```

3. Cho Unity import project xong.

## 4. Cai Vuforia Engine

Vuforia Engine `11.4.4` da duoc cai trong project va scene demo da duoc tao.

1. Tao/dang nhap tai khoan tai Vuforia Developer Portal.
2. Tao license key cho project demo.
3. Mo scene `Assets/Scenes/ARTextbookDemo.unity`.
4. Chon `ARCamera` -> mo `Vuforia Configuration` -> dan license key.
5. Play Mode da duoc cau hinh la `WEBCAM`.

## 5. Tao Image Target

Marker demo co san:

```text
D:\ARBOOK\docs\markers\BIO_CELL_001.png
D:\ARBOOK\docs\markers\SOLAR_SYSTEM_001.png
D:\ARBOOK\docs\markers\HEART_001.png
```

Project tao Image Target runtime truc tiep tu ba file PNG, khong can Target Manager database.

## 6. Gan Model Va UI

Project da co script:

```text
D:\ARBOOK\UnityProject\Assets\Scripts\ARMarkerController.cs
D:\ARBOOK\UnityProject\Assets\Scripts\ARContentApiClient.cs
D:\ARBOOK\UnityProject\Assets\Scripts\ModelController.cs
D:\ARBOOK\UnityProject\Assets\Scripts\ARInfoPanelController.cs
D:\ARBOOK\UnityProject\Assets\Scripts\DemoFallbackData.cs
```

Scene da co Canvas UI, `ARContentApiClient`, model placeholder, nut animation/reset va cac reference can thiet.

## 7. Chay Demo Webcam

1. Dam bao backend dang chay tai `http://localhost:8080`.
2. Mo scene `ARTextbookDemo`.
3. Bam `Play`.
4. Dua marker PNG da in hoac mo tren man hinh dien thoai truoc webcam.
5. Console Unity se log:
   - target found
   - target lost
   - API success/fail
   - fallback data neu backend loi

Neu API loi, demo van co fallback data de khong bi dung.

## 8. Loi Thuong Gap

- Backend connection refused: chua chay Spring Boot hoac sai port 8080.
- Railway password failed: cap nhat lai `backend\.env`, dung connection string moi tu Railway.
- Webcam khong bat: dong Zoom/Teams/Camera app dang giu webcam.
- Vuforia khong nhan marker: in marker ro net, du sang, target trong Target Manager nen co rating cao.
- Unity script error Vuforia: Vuforia package chua import thanh cong.
- Play Mode khong dung webcam: vao `Vuforia Configuration`, chon Play Mode `WEBCAM` va Camera Device dung webcam laptop.
