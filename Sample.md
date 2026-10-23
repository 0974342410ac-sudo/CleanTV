# Huong Dan & Bao Cao Toi Uu Sony BRAVIA VU1 (Build RTT7.210105.001)

Du an toi uu hoa Android TV va thay the Launcher phong cach Apple TV 4K cho **Sony BRAVIA VU1** (Realtek SoC, Android TV 11 - API 30) by Dr.Slump.

---

## 1. Ket qua thuc hien

1. **Sao luu du lieu goc:**
   - Da sao luu toan bo thong tin goi cai dat (`packages-full.txt`, `packages-enabled.txt`), thuoc tinh `getprop`, cai dat he thong (`settings global/secure/system`), cau hinh Wi-Fi va anh chup man hinh truoc khi can thiep vao thu muc: `backup/sony-bravia-vu1/`.

2. **Don dep bloatware & Telemetry (Debloat):**
   - Vo hieu hoa an toan bang lenh `pm disable-user --user 0` (khong go bo vinh vien, co the khoi phuc 100% bat ky luc nao).
   - **Cac goi da vo hieu hoa:**
     - `com.google.android.tvrecommendations` (Kenh goi y & quang cao chiem ~114 MB RAM)
     - `com.google.android.tungsten.setupwraith` (Trinh huong dan thiet lap chiem ~87 MB RAM)
     - `com.google.android.feedback` (Bao cao loi & telemetry Google)
     - `com.google.android.partnersetup` (Thu thap cai dat doi tac)
     - `com.google.android.onetimeinitializer` (Tien trinh khoi chay mot lan)
     - `com.google.android.syncadapters.calendar` (Dong bo lich & thong bao tiep thi)
     - `com.google.android.play.games` (Play Games)
     - `com.google.android.tvsetup.partnercustomizer` (Tuy bien doi tac)
     - `android.autoinstalls.config.Sony.BRAVIA_VU1` (Tu dong tai app cua doi tac)
     - `com.android.printspooler` (Dich vu in an khong dung tren TV)
     - `com.sony.dtv.smarthelp` (Tro giup Sony)
     - `com.sony.dtv.helpguidecontents` (Noi dung tai lieu huong dan)
     - `com.sony.dtv.eulaviewer` (Trinh xem thoa thuan EULA)
     - `com.sony.dtv.softwarelicense` (Trinh xem giay phep phan mem)
     - `com.sony.dtv.ime.chww` (Bo go tieng Trung)
     - `com.realtek.servicemenu` (Menu nha may)

3. **Cai dat Apple TV 4K Launcher:**
   - Ung dung Launcher: `com.gathin.tvhome` (ban tuy bien toi uu muot ma, khong quang cao).
   - Dat lam Home Mac dinh qua lenh: `cmd package set-home-activity com.gathin.tvhome/.MainActivity`.
   - 4 ung dung mac dinh o thanh Dock:
     1. TV360 (`com.viettel.tv360.tv`)
     2. YouTube (`com.google.android.youtube.tv`)
     3. Netflix (`com.netflix.ninja`)
     4. Kodi (`org.xbmc.kodi`)
   - Ho tro sap xep lai thu tu hoac chon ung dung tuy thich ngay tren Menu 4.

4. **Tiet kiem RAM & Hieu nang:**
   - Truoc khi toi uu: RAM trong dao dong ~350 MB - 480 MB.
   - Sau khi toi uu: RAM trong tang len tren ~850 MB - 1.1 GB.
   - Giao dien phan hoi ngay lap tuc, khong giat lag khi bam Home.
