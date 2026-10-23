# Huong Dan Toi Uu Moi Dong Android TV / Google TV

Bo cong cu nay da duoc cau hinh hoan chinh de ban co the mang di toi uu hoa cho **bat ky chiec TV nao khac** (Sony, Xiaomi, TCL, Hisense, Casper, Coocaa, Chromecast with Google TV, Onn Box, v.v.).

\---

## 1\. Vi tri luu tru du an

1. Thu muc du an tren may tinh: `E:\CleanTV\`
2. Ban luu tren GitHub: `https://github.com/0974342410ac-sudo/CleanTV`

\---

## 2\. Cach chay nhanh nhat (Menu 1-Click - Click la chay)

Chi chay tren Windows. Trong thu muc du an:

1. Nhap dup chuot vao file **`JustClick.exe`** (neu co; file nay khong nam trong Git), hoac
2. Chay lenh: `powershell -ExecutionPolicy Bypass -File JustClick.ps1`

Lan dau chay, neu chua co `tools\platform-tools\adb.exe`, cong cu tu tai platform-tools tu Google.

\---

## 3\. Menu tuong tac (Tieng Viet khong dau chong loi phong):

```text
==================================================================
          BO CONG CU TOI UU HOA ANDROID TV          
     Toi uu muot ma - Tiet kiem RAM - Giao dien Apple TV 4K       
==================================================================
 1. Scan TV trong mang (hoac nhap dia chi IP)
 2. Sao luu toan bo TV (Backup truoc khi sua)
 3. Toi uu \& Don dep ung dung rac (Debloat)
 4. Cai dat Launcher \& Sap xep 4 app o thanh Dock
 5. Bat / Tat chan quang cao (AdGuard Private DNS)
 6. Go bo ung dung tren TV (Remove/Uninstall Apps)
 7. Chup anh man hinh TV (Screenshot)
 8. Khoi dong lai TV (Reboot)
 9. Khoi phuc TV ve nguyen ban (Restore All)
 10. Thoat
------------------------------------------------------------------
\*(Tat ca cac buoc va tinh nang deu co phim \[0] de quay lai menu chinh bat cu luc nao)\*
```

\---

## 4\. Cac buoc thuc hien tren mot TV moi bat ky

### Buoc 1: Chuan bi tren TV moi

1. Dam bao TV va may tinh dang ket noi **cung mot mang Wi-Fi hoac mang day LAN**.
2. Tren TV, vao **Cai dat (Settings) -> Tuy chon thiet bi (Device Preferences / System) -> Gioi thieu (About)**.
3. Tim den dong **So ban dung (Build)** va **nhan phim OK lien tuc 7 lan** de bat Che do nha phat trien.
4. Quay lai menu truoc -> chon **Tuy chon nha phat trien (Developer options)** -> Bat **Go loi USB (USB debugging)** (va Network debugging neu co).
5. Vao **Cai dat mang** xem dia chi IP cua TV (vi du: `192.168.1.50`).

### Buoc 2: Thao tac tren may tinh

1. Chay **`JustClick.exe`** hoac **`JustClick.ps1`** (xem muc 2).
2. Chon **Phim 1**: Scan tu dong tim TV trong mang hoac nhap dia chi IP cua TV.
*(Neu tren man hinh TV hien pop-up "Cho phep go loi USB? / Allow USB debugging?", dung remote tick vao "Luon cho phep" va bam OK)*.
3. Chon **Phim 2**: Sao luu du lieu goc cua TV truoc khi lam bat cu dieu gi (an toan tuyet doi).
4. Chon **Phim 3**: Don dep ung dung rac (Debloat):

   * Cong cu da co san cac cau hinh mau cho: Sony Bravia, Xiaomi (PatchWall), TCL, hoac hon 100 dong TV khac nam trong thu muc `examples/`.
5. Chon **Phim 4**: Cai dat Launcher \& Sap xep 4 app o thanh Dock:

   * Lua chon 1: Cai dat voi 4 ung dung mac dinh (TV360, YouTube, Netflix, Kodi).
   * Lua chon 2: Sap xep lai vi tri thu tu giua 4 app (vi du: `2,1,4,3`).
   * Lua chon 3: Tuy chon 4 app bat ky tu danh sach ung dung da cai tren TV de dua ra thanh Dock.
6. Chon **Phim 5**: Bat AdGuard DNS de chan quang cao neu muon.
7. Chon **Phim 8**: Khoi dong lai TV de tan huong giao dien moi muot ma, giai phong RAM.

