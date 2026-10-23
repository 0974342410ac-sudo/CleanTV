-- Tên ô trong bảng cài đặt nhanh (phím cài đặt nhanh trên remote) của máy chiếu XGIMI DOHKO.
-- Bảng đọc tên từ /data/data/com.android.newsettings/databases/misckey_config.db, không từ chuỗi tài nguyên, nên
-- bản dịch VietHoa không chạm tới. Mỗi lần mở, newsettings tải cấu hình bảng từ máy chủ XGIMI
-- (MisckeyManager.fetchConfigData -> MisckeyConfigDao.saveMisckeyConfig) và ghi đè tên tiếng Trung, nên sửa
-- thẳng một lần không giữ được. Vì vậy ở đây đặt trigger: mỗi khi app chèn hay đổi tên một ô, trigger thay
-- tên tiếng Trung bằng tên tiếng Việt. Room không kiểm tra trigger (chỉ identity_hash), nên app vẫn mở được;
-- trigger mất nếu bản cập nhật newsettings dựng lại cơ sở dữ liệu: chạy lại script.
-- Áp dụng: tools\xgimi-vi\dohko-quick-panel-vi.ps1 -TvHost <ip>. Ánh xạ theo tên gốc (máy chủ có thể đổi icon).
DROP TRIGGER IF EXISTS vi_func_name_insert;
DROP TRIGGER IF EXISTS vi_func_name_update;

CREATE TRIGGER vi_func_name_insert AFTER INSERT ON MisckeyConfigFuncDB BEGIN
    UPDATE MisckeyConfigFuncDB SET name = CASE NEW.name
        WHEN '一键画面校正' THEN 'Hiệu chỉnh một chạm'
        WHEN '手动梯形校正' THEN 'Hình thang thủ công'
        WHEN '手动调焦' THEN 'Lấy nét thủ công'
        WHEN '环境自适应' THEN 'Thích ứng môi trường'
        WHEN '亮度调节' THEN 'Độ sáng'
        WHEN '游戏增强' THEN 'Tăng cường game'
        WHEN '风格滤镜' THEN 'Bộ lọc màu'
        WHEN '信号源' THEN 'Nguồn vào'
        WHEN '网络' THEN 'Mạng'
        WHEN '蓝牙' THEN 'Bluetooth'
        WHEN '儿童模式' THEN 'Chế độ trẻ em'
        WHEN '任意门' THEN 'Cửa thần kỳ'
        WHEN 'U盘' THEN 'USB'
        WHEN '音乐控件' THEN 'Điều khiển nhạc'
        ELSE NEW.name END
    WHERE id = NEW.id;
END;

-- Cùng bảng dịch khi app đổi tên một ô có sẵn (recursive_triggers tắt, nên lệnh UPDATE bên trong không gọi lại)
CREATE TRIGGER vi_func_name_update AFTER UPDATE OF name ON MisckeyConfigFuncDB BEGIN
    UPDATE MisckeyConfigFuncDB SET name = CASE NEW.name
        WHEN '一键画面校正' THEN 'Hiệu chỉnh một chạm'
        WHEN '手动梯形校正' THEN 'Hình thang thủ công'
        WHEN '手动调焦' THEN 'Lấy nét thủ công'
        WHEN '环境自适应' THEN 'Thích ứng môi trường'
        WHEN '亮度调节' THEN 'Độ sáng'
        WHEN '游戏增强' THEN 'Tăng cường game'
        WHEN '风格滤镜' THEN 'Bộ lọc màu'
        WHEN '信号源' THEN 'Nguồn vào'
        WHEN '网络' THEN 'Mạng'
        WHEN '蓝牙' THEN 'Bluetooth'
        WHEN '儿童模式' THEN 'Chế độ trẻ em'
        WHEN '任意门' THEN 'Cửa thần kỳ'
        WHEN 'U盘' THEN 'USB'
        WHEN '音乐控件' THEN 'Điều khiển nhạc'
        ELSE NEW.name END
    WHERE id = NEW.id;
END;

-- Dịch luôn các tên đang có (lệnh này gọi trigger cập nhật ở trên)
UPDATE MisckeyConfigFuncDB SET name = name;
