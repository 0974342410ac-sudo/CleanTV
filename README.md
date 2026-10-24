# CleanTV

Dọn ứng dụng thừa, quảng cáo và theo dõi trên Android TV, Google TV và máy chiếu, rồi thay màn hình chính
bằng một launcher kiểu Apple TV, giao diện tiếng Việt.

Mọi thay đổi đều đảo ngược được: ứng dụng chỉ bị **tắt** (`pm disable-user`), không bị gỡ, và luôn có cách
khôi phục (`tools/restore.ps1`, nút "Khôi phục" trong ứng dụng cập nhật USB và ứng dụng trên điện thoại).

## Gồm những gì

| Thành phần | Thư mục | Dùng để |
|---|---|---|
| Launcher `com.gathin.tvhome` | `launcher/app/` | Màn hình chính kiểu Apple TV: dock 4 ứng dụng, lời chào, hình nền đổi theo giờ, ẩn ứng dụng |
| Ứng dụng cập nhật bằng USB | `launcher/updater/`, `updater/` | Cập nhật máy chiếu / TV box không cần máy tính: cài launcher, tắt ứng dụng thừa, Việt hoá |
| Cập nhật từ máy tính | `updater/CapNhat.ps1`, `updater/build-updater.ps1` | Cùng việc đó từ máy tính Windows qua Wi-Fi: cài ứng dụng cập nhật của gói USB lên TV và cho nó tự chạy |
| Cập nhật qua điện thoại | `launcher/updater/` (bản `dienthoai`), `updater/build-phone.ps1` | Cùng việc đó từ điện thoại Android qua Wi-Fi, tự tải bản mới từ GitHub Releases |
| Công cụ dòng lệnh (Windows) | `tools/`, `launcher/build-and-install.ps1` | Debloat, khôi phục, cài launcher, đẩy giao diện (theme) qua ADB |
| Danh sách debloat theo máy | `examples/` | Hơn 100 dòng TV (Sony, Xiaomi, TCL, Hisense, Philips, XGIMI, onn...), mỗi máy một `debloat.txt` có ghi chú |
| Việt hoá XGIMI | `tools/xgimi-vi/` | Bản dịch Cài đặt, bảng điều khiển nhanh, menu nguồn và 20 ứng dụng hệ thống XGIMI |

## Chuẩn bị TV

1. TV và máy tính cùng mạng Wi-Fi hoặc mạng dây.
2. Trên TV: **Cài đặt → Tuỳ chọn thiết bị → Giới thiệu**, bấm OK 7 lần vào **Bản dựng (Build)** để bật
   chế độ nhà phát triển.
3. **Tuỳ chọn nhà phát triển** → bật **Gỡ lỗi USB** (và **Gỡ lỗi mạng** nếu có).
4. Xem địa chỉ IP của TV trong phần cài đặt mạng.
5. Lần đầu kết nối, TV hỏi "Cho phép gỡ lỗi USB?": tích **Luôn cho phép** rồi bấm OK.

## Cách dùng

**Debloat và cài đặt từ máy tính:** chọn danh sách của máy trong `examples/`, rồi

    powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\<máy>\debloat.txt
    powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
    powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 <thư mục có config.json>   # giao diện
    powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\<máy>\debloat.txt

**Không có máy tính (máy chiếu XGIMI, onn 4K box):** chép thư mục `USB-CapNhat` vào USB, cài
`CapNhatMayChieu.apk` trên máy rồi bấm "Cập nhật". Xem [updater/HUONG-DAN-USB.txt](updater/HUONG-DAN-USB.txt)
và [updater/HUONG-DAN-USB-XGIMI.txt](updater/HUONG-DAN-USB-XGIMI.txt).

**Từ máy tính, không cắm USB vào TV:** giải nén `PC-CapNhat.zip`, nhấp đúp `CapNhat.cmd` (hoặc `KhoiPhuc.cmd`):
chương trình tìm TV trên Wi-Fi, cài ứng dụng cập nhật của gói USB lên TV và cho nó tự chạy, nên kết quả giống hệt
gói USB. Xem [updater/HUONG-DAN.txt](updater/HUONG-DAN.txt).

**Từ điện thoại Android:** cài `CapNhatTV-DienThoai.apk` (GitHub Releases), nhập IP của TV hoặc bấm "Tìm TV",
rồi bấm "Cập nhật". Xem [updater/HUONG-DAN-DIENTHOAI.txt](updater/HUONG-DAN-DIENTHOAI.txt).

## Build

Cần JDK 17 và Android SDK (đường dẫn trong [CLAUDE.md](CLAUDE.md)), cùng `tools/platform-tools` tải từ Google.

    powershell -ExecutionPolicy Bypass -File updater\build-usb.ps1      # launcher + gói USB-CapNhat vào dist/
    powershell -ExecutionPolicy Bypass -File updater\build-usb.ps1 -XgimiDohko   # ... + gói XGIMI DOHKO
    powershell -ExecutionPolicy Bypass -File updater\build-phone.ps1    # bản điện thoại (-Publish: GitHub Release)

APK được ký bằng `%USERPROFILE%\.android\debug.keystore`. Mọi máy build phải dùng **cùng một keystore**
(chia sẻ riêng, không đưa lên Git); khác keystore thì APK mới không cài đè được lên bản đã có trên TV.

## Không có trong Git

`backup/` (dữ liệu từng TV: cài đặt, số serial, Wi-Fi), `dist/`, `prebuilt/`, file build, `tools/platform-tools`,
`tools/venv` và `tools/adbkey` (khoá ADB riêng của mỗi máy tính). Xem [.gitignore](.gitignore).
