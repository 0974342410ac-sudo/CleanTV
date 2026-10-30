# Karaoke (com.gathin.karaoke)

Ứng dụng karaoke cho Android TV / Google TV / máy chiếu, điều khiển hoàn toàn bằng remote.

Bố cục như đầu karaoke: thanh menu bên trái, trang ở giữa, thanh **Bài kế** ở dưới (số bài đang chờ, bài kế,
nút **Hát bài kế**).

- **Trang chủ**: các hàng Thịnh hành tháng này, Ca sĩ nổi bật, Thể loại, Hát gần đây, Yêu thích.
- **Tìm kiếm**: gõ tên bài hoặc ca sĩ (hoặc bấm **Nói**), chữ "karaoke" tự thêm vào. Nút Search / micrô trên
  remote (nếu hệ thống không giữ) cũng mở trang này.
- **Thịnh hành**: video karaoke đăng trong tháng này (bộ lọc "Tháng này" của YouTube).
- **Ca sĩ**: lưới ca sĩ có ảnh (ảnh kênh chính chủ: kênh nghệ sĩ YouTube, trùng tên; lưu lại sau lần đầu; chưa
  có ảnh thì hiện chữ cái đầu). OK → bài của ca sĩ đó; Back → về lưới ca sĩ. Danh sách ở
  `src/main/res/values/singers.xml`.
- **Thể loại**: 14 ô màu (Bolero, Nhạc trẻ, Song ca, Nhạc sống, Remix, Nonstop, Nonstop có lời, Nhạc Trịnh, Liên khúc, Thiếu nhi,
  Tone nữ, Tone nam, Nhạc Hoa lời Việt, Dân ca).
- **Yêu thích**, **Đã hát** (100 bài gần nhất), **Danh sách chọn**: lưu trên TV, tắt app vẫn còn.
- **OK vào một bài**: menu *Hát ngay* (sẵn chọn: OK OK là hát) · *Thêm vào danh sách* · *Ưu tiên (hát kế tiếp)*
  · *Yêu thích*. **Giữ OK** hoặc nút **Menu**: thêm thẳng vào danh sách.
- Trong **Danh sách chọn**, OK vào bài: *Hát ngay* · *Đưa lên đầu* · *Xoá* · *Xoá cả danh sách*.
- Back: từ trang ca sĩ/thể loại về lưới; từ trang về thanh menu; từ thanh menu về Trang chủ; rồi thoát.
- Kết quả cuộn xuống cuối tự tải thêm.
- **Điện thoại** (mục cuối thanh menu): mã QR và mã PIN 4 số. Điện thoại cùng Wi-Fi quét QR (hoặc mở
  `http://<IP TV>:8687` rồi nhập PIN) là có trang chọn bài, không cần cài app, iPhone cũng được: tìm bài (gõ hoặc
  bấm micrô của bàn phím), Thịnh hành, thể loại, ca sĩ; *＋ Thêm*, *Hát kế*, *Hát ngay*; xem và sắp lại danh
  sách; dừng / tới / lùi 10 giây / bài kế / thoát bài. Nhiều điện thoại dùng cùng lúc được. *Đổi mã* trên TV bắt
  mọi điện thoại kết nối lại. Máy chủ `PhoneRemote.java` (cổng 8687; launcher dùng 8686), trang `res/raw/phone.html`.
  Chỉ hát được khi app Karaoke đang mở trên TV (Android không cho app chạy nền mở màn hình).
  Mã QR (có PIN) luôn hiện: ở đầu thanh **Bài kế** dưới màn hình chính, và ở góc trên bên phải khi đang hát.
- Nút **Phát:** (dưới thanh menu) đổi giữa (trong app / YouTube)
  - *trong ứng dụng* (mặc định): trình phát YouTube nhúng trong WebView, hết bài tự sang bài kế tiếp
    trong hàng chờ. OK = dừng/tiếp, Trái/Phải = lùi/tới 10 giây, Tua nhanh / Next / Kênh+ = bỏ qua bài,
    Xuống = xem bài đang hát và bài kế, Back = thoát. Còn 20 giây thì hiện "Sắp tới: <bài kế>".
    **Lên** (hoặc Search / Menu) = dải tìm bài thấp ở trên cùng, kết quả xếp một hàng ngang, video vẫn phát và vẫn
    thấy lời ở dưới. Bốn mục: *Thịnh hành* (mở ra là mục này), *Yêu thích*, *Ca sĩ* (gõ tên để lọc, không cần dấu; OK
    vào ca sĩ ra bài của người đó, Back về danh sách ca sĩ), *Thể loại* (tương tự). OK vào ô tìm mở bàn phím riêng
    nằm trong dải, không che lời hát (không gõ dấu, YouTube vẫn tìm ra; nút 123 đổi hàng trên thành số; giữ OK vào ⌫ là
    xoá hết), hoặc bấm **Nói**. OK = thêm vào danh sách,
    giữ OK = hát kế tiếp, Menu = thêm/bỏ Yêu thích, Back = đóng bàn phím, rồi đóng dải.
  - *ứng dụng YouTube*: mở bài trong TizenTube (nếu có), YouTube for Android TV, hoặc YouTube. App
    YouTube không báo khi hết bài, nên sau mỗi bài bấm Back rồi OK vào **Hát bài kế** (nút này được
    chọn sẵn khi quay lại).
  - Bài nào chủ kênh cấm nhúng (lỗi 101/150/152/153) thì tự mở trong app YouTube.

## Build và cài

    cd E:\CleanTV\launcher
    set JAVA_HOME=E:\buildtools\jdk\jdk-17.0.20.1+1
    gradlew :karaoke:testReleaseUnitTest :karaoke:assembleRelease

APK: `karaoke\build\outputs\apk\release\karaoke-release.apk` (~200 KB), ký bằng debug keystore như launcher.
Cài (chỉ khi chủ máy cho phép):

    E:\CleanTV\tools\platform-tools\adb.exe connect 192.168.31.147:5555
    E:\CleanTV\tools\platform-tools\adb.exe -s 192.168.31.147:5555 install -r karaoke\build\outputs\apk\release\karaoke-release.apk

Gỡ: `adb uninstall com.gathin.karaoke`. Ứng dụng có biểu tượng trong hàng ứng dụng Android TV (banner)
và trong launcher CleanTV.

Thử bộ đọc trên một trang kết quả thật đã lưu:
`gradlew :karaoke:testReleaseUnitTest -PrealPage=E:\duong-dan\results.html`.

## Nguồn tìm kiếm và rủi ro

Không dùng API key, không tài khoản: ứng dụng đọc chính trang `https://www.youtube.com/results?search_query=…`
(bộ lọc "Video", hl=vi, gl=VN) như một trình duyệt máy tính; dữ liệu nằm trong `var ytInitialData = {…}`.
Trang sau lấy qua `youtubei/v1/search` với token "continuation" của trang trước. (Invidious/Piped đã thử
ngày 28/10/2026: instance công khai tắt API tìm kiếm hoặc không trả lời, nên không dùng.)

Rủi ro:
- **YouTube đổi cấu trúc trang**: bộ đọc tìm `videoRenderer` (và `lockupViewModel` mới hơn) ở bất cứ đâu trong
  cây JSON nên chịu được việc xếp lại bố cục, nhưng nếu đổi tên renderer hoặc bỏ `ytInitialData` thì app báo
  "YouTube đã đổi trang kết quả" và cần sửa `SearchParser.java`.
- **Bị chặn / giới hạn**: tìm quá nhiều từ một IP có thể bị trả 429 hoặc trang xác minh (app báo "tạm thời từ
  chối"). Dùng trong nhà thì hiếm gặp.
- **Điều khoản YouTube** không cho phép đọc trang tự động; dùng cá nhân trong gia đình, không phát hành.
- **Trình phát nhúng**: YouTube có thể từ chối WebView (lỗi 152/153) hoặc chủ kênh cấm nhúng; khi đó app tự mở
  app YouTube. WebView cũ trên máy Android 6 có thể phát kém: chuyển nút **Phát:** sang ứng dụng YouTube.
  Trình phát nhúng có quảng cáo như YouTube thường.
- `clientVersion` cho trang sau lấy từ trang đầu; nếu thiếu thì dùng giá trị cố định trong `YouTubeSearch.java`,
  có thể cũ dần.

## Chưa thử trên TV

Đã thấy chạy trên onn box (Google TV 14, 28/10/2026): tìm kiếm có ảnh, trình phát nhúng phát được (AV1). Bản 0.1
hiện hộp "mở bằng" khi bấm Nói tên bài vì box có 2 màn hình nói (Katniss, Google TTS); bản 0.2 chọn thẳng Katniss.
Bản 1.1.2 (30/10/2026): dải tìm, bàn phím riêng, hàng chờ tự sang bài đều chạy trên box này.
Chưa kiểm: giọng
nói trên từng máy (Google TV mở màn hình nói của Google; XGIMI DOHKO dùng dịch vụ nhận dạng + xin quyền micrô;
nút micrô trên remote thường bị Google Assistant giữ, nên dùng nút **Nói** trên màn hình), và bàn phím ảo.
