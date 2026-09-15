(() => {
  'use strict';

  const STORAGE_KEY = 'retraUiLanguage';
  const SUPPORTED = new Set(['en', 'vi', 'id']);

  const vi = {
    'Library':'Thư viện','Refresh library':'Làm mới thư viện','History':'Lịch sử','More':'Thêm','Default':'Mặc định',
    'No ROMs in your library':'Chưa có ROM nào trong thư viện','Tap + to add a ROM or ROM hack file from your device.':'Nhấn + để thêm tệp ROM hoặc ROM hack từ thiết bị.',
    'Edit name':'Đổi tên','Change background':'Đổi nền','Change Cover':'Đổi ảnh bìa','Local file':'Tệp cục bộ','ROM file':'Tệp ROM','Ready':'Sẵn sàng','Local Library':'Thư viện cục bộ','In library':'Trong thư viện','Resume':'Tiếp tục','Recent saves':'Bản lưu gần đây','0 entries':'0 mục',
    'Filter':'Bộ lọc','Set as default':'Đặt làm mặc định',"Remember this ROM's current categories":'Ghi nhớ các danh mục hiện tại của ROM này','Reset':'Đặt lại',"Return this ROM to its saved default":'Đưa ROM này về danh mục mặc định đã lưu','Add to categories':'Thêm vào danh mục','Select where this ROM should appear in your Library.':'Chọn nơi ROM này sẽ xuất hiện trong Thư viện.','No categories yet. Create one in More → Categories.':'Chưa có danh mục. Hãy tạo một danh mục trong Thêm → Danh mục.','Close':'Đóng','Save':'Lưu','No recent history yet.':'Chưa có lịch sử gần đây.',
    'Incognito mode':'Chế độ ẩn danh','Pauses playing history':'Tạm dừng ghi lịch sử chơi','Statistics':'Thống kê','Categories':'Danh mục','Color Style':'Phong cách màu','Classic':'Cổ điển','Data and Storage':'Dữ liệu và bộ nhớ','Backup, restore, storage':'Sao lưu, khôi phục và bộ nhớ','Settings':'Cài đặt','Support Us':'Ủng hộ chúng tôi','About':'Giới thiệu','Help':'Trợ giúp',
    'Choose the color vibe used while playing. This changes only the game image, not the Retra interface.':'Chọn phong cách màu khi chơi. Thiết lập này chỉ thay đổi hình ảnh trò chơi, không thay đổi giao diện Retra.','Original balanced retro colors':'Màu retro cân bằng nguyên bản','Vivid':'Rực rỡ','Brighter, richer and more punchy':'Sáng hơn, đậm hơn và nổi bật hơn','Warm':'Ấm','Softer golden retro tone':'Tông vàng retro dịu hơn','Muted':'Dịu màu','Softer color and gentler contrast':'Màu dịu hơn và độ tương phản nhẹ hơn','Classic uses the provided reference image as the baseline look.':'Cổ điển dùng hình tham chiếu được cung cấp làm diện mạo cơ sở.',
    'Storage':'Bộ nhớ','Retra storage':'Bộ nhớ Retra','Open the Retra app folder in Android Files':'Mở thư mục Retra trong ứng dụng Tệp của Android','Storage usage':'Dung lượng sử dụng','Checking storage…':'Đang kiểm tra bộ nhớ…',"Retra uses your phone's internal storage":'Retra sử dụng bộ nhớ trong của điện thoại','Backup and restore':'Sao lưu và khôi phục','Create backup':'Tạo bản sao lưu','Choose what to include, then save a .retra file anywhere':'Chọn dữ liệu cần sao lưu, sau đó lưu tệp .retra ở bất kỳ đâu','Restore backup':'Khôi phục bản sao lưu','Restore data from an existing Retra .retra backup':'Khôi phục dữ liệu từ bản sao lưu .retra hiện có','Restore from Google Drive':'Khôi phục từ Google Drive','Recover saves, library, statistics and settings after reinstalling Retra':'Khôi phục bản lưu, thư viện, thống kê và cài đặt sau khi cài lại Retra',
    'Retra backups do not include ROM or BIOS files. Save files, save states, cheats, layouts, artwork, library metadata, statistics, and portable app settings can be recovered separately.':'Bản sao lưu Retra không bao gồm tệp ROM hoặc BIOS. Bản lưu, save state, cheat, bố cục, ảnh bìa, siêu dữ liệu thư viện, thống kê và cài đặt có thể được khôi phục riêng.','Google Drive':'Google Drive','Automatic Google Drive backup':'Tự động sao lưu lên Google Drive','Protect saves, statistics and settings across reinstalls and devices':'Bảo vệ bản lưu, thống kê và cài đặt khi cài lại hoặc đổi thiết bị','Backup Now':'Sao lưu ngay','Upload a validated backup directly to My Drive/Retra Backups':'Tải bản sao lưu đã kiểm tra trực tiếp lên My Drive/Retra Backups','Google Drive settings':'Cài đặt Google Drive','Connect a Google account to manage Drive backup':'Kết nối tài khoản Google để quản lý sao lưu Drive',
    'Choose what you want to include. The backup will be saved as a single':'Chọn dữ liệu bạn muốn đưa vào. Bản sao lưu sẽ được lưu thành một tệp','file.':'duy nhất.','Game data':'Dữ liệu trò chơi','Game saves':'Bản lưu trò chơi','Battery saves used by your games':'Bản lưu pin mà trò chơi sử dụng','Save states':'Save state','Manual, quick, and automatic save states':'Save state thủ công, nhanh và tự động','Cheats':'Cheat','Saved cheat codes and enabled-state data':'Mã cheat đã lưu và trạng thái bật/tắt','Library and metadata':'Thư viện và siêu dữ liệu','Favorites, categories, playtime, and library metadata':'Yêu thích, danh mục, thời gian chơi và siêu dữ liệu thư viện','Controller layouts':'Bố cục tay cầm','Screen Editor and controller positioning':'Trình chỉnh sửa màn hình và vị trí nút điều khiển','Artwork':'Ảnh bìa','Downloaded covers and backgrounds':'Ảnh bìa và hình nền đã tải','App settings':'Cài đặt ứng dụng','Portable Retra settings; device-specific permissions are excluded':'Cài đặt Retra có thể chuyển thiết bị; không bao gồm quyền riêng của thiết bị',
    'Appearance':'Giao diện','Theme, dark mode, accents':'Chủ đề, chế độ tối, màu nhấn','Video':'Hình ảnh','Renderer, scaling, frame rate':'Bộ dựng hình, tỉ lệ và tốc độ khung hình','Audio':'Âm thanh','Volume, latency, synchronization':'Âm lượng, độ trễ và đồng bộ','Layouts':'Bố cục','Controls, buttons, screen placement':'Điều khiển, nút và vị trí màn hình','Misc':'Khác','Saves, screenshots, vibration, library':'Bản lưu, ảnh chụp, rung và thư viện','Advanced':'Nâng cao','Core options, BIOS, debugging':'Tùy chọn lõi, BIOS và gỡ lỗi','Fonts':'Phông chữ','Choose the Retra interface font':'Chọn phông chữ giao diện Retra','Language':'Ngôn ngữ','Choose the Retra interface language':'Chọn ngôn ngữ giao diện Retra','Retra version, licenses, information':'Phiên bản Retra, giấy phép và thông tin',
    'Theme':'Chủ đề','System':'Theo hệ thống','Light':'Sáng','Dark':'Tối','Pure black dark mode':'Chế độ tối đen tuyệt đối','Use a deeper black palette for OLED screens':'Dùng màu đen sâu hơn cho màn hình OLED','Translucent mode':'Chế độ trong suốt','Add a soft glass effect across cards and panels':'Thêm hiệu ứng kính nhẹ cho thẻ và bảng',
    'Stretch to fit screen':'Kéo giãn vừa màn hình','Stretch video inside the saved game-screen frame':'Kéo giãn hình ảnh trong khung màn hình trò chơi đã lưu','Controller Opacity':'Độ trong suốt nút điều khiển','Adjust the transparency of on-screen game controls':'Điều chỉnh độ trong suốt của các nút điều khiển trên màn hình','Screen size':'Kích thước màn hình','Preview and adjust in landscape mode':'Xem trước và điều chỉnh ở chế độ ngang','Screen orientation':'Hướng màn hình','Auto rotate':'Tự động xoay','Frameskip':'Bỏ khung hình','0 = smoothest (~60 FPS); 1 = ~30 FPS. Leave at 0 unless the device struggles.':'0 = mượt nhất (~60 FPS); 1 = ~30 FPS. Hãy để 0 trừ khi thiết bị chạy chậm.','Hardware rendering':'Kết xuất phần cứng','Use Android GPU compositing for the game screen':'Dùng GPU Android để ghép hình màn hình trò chơi','Linear filtering':'Lọc tuyến tính','Smooth pixels when scaling':'Làm mượt điểm ảnh khi phóng to/thu nhỏ','Video shader':'Shader hình ảnh','Off • no extra GPU cost':'Tắt • không tốn thêm GPU',
    'Screen editor':'Trình chỉnh sửa màn hình','Portrait and landscape use separate layouts. Rotate the device to edit that orientation. Back and + appear only in this editor and disappear during gameplay. Drag controls to move them and use the scale handle to resize them.':'Dọc và ngang dùng bố cục riêng. Xoay thiết bị để chỉnh bố cục tương ứng. Nút Quay lại và + chỉ xuất hiện trong trình chỉnh sửa và sẽ ẩn khi chơi. Kéo nút để di chuyển và dùng tay nắm tỉ lệ để thay đổi kích thước.','Tap or drag screen • Drag edges or resize handle • Hold for modes':'Chạm hoặc kéo màn hình • Kéo cạnh hoặc tay nắm để đổi kích thước • Giữ để chọn chế độ','Make fullscreen':'Phủ đầy màn hình','Make centered':'Căn giữa','Best Fit':'Vừa nhất','Best scaling (4x)':'Tỉ lệ tốt nhất (4x)','Break aspect ratio':'Bỏ khóa tỉ lệ khung hình','Grid':'Lưới','Remove control':'Xóa nút','Add control':'Thêm nút','Button A':'Nút A','Button B':'Nút B','Quick load':'Tải nhanh','Quick save':'Lưu nhanh','Fast forward':'Tua nhanh','Screenshot':'Chụp màn hình','Reset layout':'Đặt lại bố cục','Cancel':'Hủy','Tip: tap a control to select it. A scale handle appears beside the selected control so you can resize it.':'Mẹo: chạm một nút để chọn. Tay nắm tỉ lệ sẽ xuất hiện bên cạnh để bạn thay đổi kích thước.',
    'Enable sound':'Bật âm thanh','Turn emulator audio on or off':'Bật hoặc tắt âm thanh trình giả lập','Sound volume':'Âm lượng','Master emulator volume':'Âm lượng tổng của trình giả lập','Sound frequency':'Tần số âm thanh','High quality':'Chất lượng cao','Balanced':'Cân bằng','Lower CPU usage':'Giảm tải CPU','Standard GBA controls':'Điều khiển GBA tiêu chuẩn','New layout profile':'Bố cục mới','Name this controller layout. After you press OK, Retra will open the Screen Editor for this profile.':'Đặt tên cho bố cục điều khiển. Sau khi nhấn OK, Retra sẽ mở Trình chỉnh sửa màn hình cho bố cục này.',
    'Enable cheats':'Bật cheat','Allow cheat codes while playing':'Cho phép dùng mã cheat khi chơi','ROM patching':'Vá ROM','Auto IPS/UPS/BPS patching when a matching patch is available':'Tự động áp dụng bản vá IPS/UPS/BPS khi có bản vá phù hợp','Automatic game artwork':'Tự động tìm ảnh trò chơi','Find and cache matching covers in the background':'Tìm và lưu ảnh bìa phù hợp trong nền','Artwork on Wi-Fi only':'Chỉ tải ảnh qua Wi‑Fi','Reduce mobile-data use for automatic cover downloads':'Giảm dữ liệu di động khi tự động tải ảnh bìa','Retry missing artwork':'Thử lại ảnh còn thiếu','Search again for ROMs that still use fallback colors':'Tìm lại ảnh cho các ROM vẫn đang dùng màu dự phòng','Auto save & load':'Tự động lưu và tải','Quick-save on background/exit and keep automatic resume available on next launch':'Lưu nhanh khi chạy nền/thoát và cho phép tiếp tục tự động ở lần mở sau','Emulation speed':'Tốc độ giả lập','Speed button':'Nút tốc độ','Press to toggle':'Nhấn để bật/tắt','Confirm on close/reset':'Xác nhận khi đóng/đặt lại','Ask before closing or resetting a game':'Hỏi trước khi đóng hoặc đặt lại trò chơi','Full screen mode':'Chế độ toàn màn hình','Make games fullscreen by hiding system bars':'Ẩn thanh hệ thống để trò chơi hiển thị toàn màn hình','Immersive mode':'Chế độ nhập vai','Hide gesture/navigation bars while a game is running':'Ẩn thanh cử chỉ/điều hướng khi đang chơi','Open app folder':'Mở thư mục ứng dụng','Open Retra storage in Android Files':'Mở bộ nhớ Retra trong ứng dụng Tệp Android',
    'CPU profile':'Cấu hình CPU','Automatic':'Tự động','Use BIOS':'Dùng BIOS','Enable low-level BIOS emulation':'Bật giả lập BIOS mức thấp','Boot BIOS':'Khởi động BIOS','Show BIOS screen on game start':'Hiện màn hình BIOS khi bắt đầu trò chơi','BIOS file':'Tệp BIOS','No BIOS selected':'Chưa chọn BIOS','Cartridge save type':'Kiểu lưu cartridge','Link sync check':'Kiểm tra đồng bộ Link','Remote Link state-integrity interval; smaller values check more often':'Khoảng kiểm tra tính toàn vẹn của Remote Link; giá trị nhỏ sẽ kiểm tra thường xuyên hơn','Speed optimization':'Tối ưu tốc độ','Enable mGBA idle-loop optimization':'Bật tối ưu vòng lặp chờ của mGBA','Mosaic effect':'Hiệu ứng mosaic','Enable GBA mosaic rendering':'Bật kết xuất hiệu ứng mosaic của GBA','Reset advanced settings':'Đặt lại cài đặt nâng cao','Restore advanced options to defaults':'Khôi phục tùy chọn nâng cao về mặc định',
    'Poppins':'Poppins','Alternative':'Tùy chọn khác','Inter':'Inter','Default • clean and highly readable':'Mặc định • rõ ràng và dễ đọc','Clean and highly readable':'Rõ ràng và dễ đọc','Manrope':'Manrope','Modern rounded sans-serif':'Sans-serif bo tròn hiện đại','DM Sans':'DM Sans','Balanced and compact':'Cân bằng và gọn','English':'Tiếng Anh','Vietnamese interface':'Giao diện tiếng Việt tự nhiên','Indonesian interface':'Giao diện tiếng Indonesia tự nhiên',
    'Version':'Phiên bản','Check for updates':'Kiểm tra cập nhật','Automatically checked in the background':'Tự động kiểm tra trong nền','Retra is an independent emulator frontend. No copyrighted game software or ROM files are included with this product.':'Retra là giao diện giả lập độc lập. Sản phẩm không kèm phần mềm trò chơi có bản quyền hoặc tệp ROM.','Privacy Policy':'Chính sách quyền riêng tư','Retra Privacy Policy':'Chính sách quyền riêng tư của Retra','Local game data.':'Dữ liệu trò chơi cục bộ.','Network features.':'Tính năng mạng.','Google Drive.':'Google Drive.','No advertising or analytics SDK.':'Không có SDK quảng cáo hoặc phân tích.','Permissions.':'Quyền truy cập.','Data deletion.':'Xóa dữ liệu.','Uninstall and reinstall.':'Gỡ cài đặt và cài lại.','Policy updates.':'Cập nhật chính sách.',
    'Edit categories':'Chỉnh sửa danh mục','You have no categories. Tap the Add button to create one for organizing your library.':'Bạn chưa có danh mục. Nhấn Thêm để tạo danh mục và sắp xếp thư viện.','Add':'Thêm','Add category':'Thêm danh mục','Create a category for organizing ROMs in your Library.':'Tạo danh mục để sắp xếp ROM trong Thư viện.','Overview':'Tổng quan','Total playtime':'Tổng thời gian chơi','Completed games':'Trò chơi đã hoàn thành','Entries':'Mục','Started':'Đã bắt đầu','Favorites':'Yêu thích','Recently played':'Chơi gần đây','Saves':'Bản lưu','Battery saves':'Bản lưu pin','Backups':'Bản sao lưu','Systems':'Hệ máy','Playtime by ROM':'Thời gian chơi theo ROM','No playtime data yet.':'Chưa có dữ liệu thời gian chơi.',
    'Support Retra':'Ủng hộ Retra','If you enjoy using Retra, you can support development through Buy Me a Coffee.':'Nếu bạn thích Retra, bạn có thể ủng hộ quá trình phát triển qua Buy Me a Coffee.','Need help?':'Cần trợ giúp?','Use Help for setup guides, controller help, save import instructions, and troubleshooting tips for ROM scanning and emulator settings.':'Dùng Trợ giúp để xem hướng dẫn cài đặt, điều khiển, nhập bản lưu và cách xử lý lỗi quét ROM hoặc cài đặt giả lập.','Open help center':'Mở trung tâm trợ giúp',
    'Add to Favourites':'Thêm vào Yêu thích','Keep this ROM at the front of your Library':'Giữ ROM này ở đầu Thư viện','Remove from Library':'Xóa khỏi Thư viện','Hide this ROM • saves and game data are kept':'Ẩn ROM này • bản lưu và dữ liệu trò chơi vẫn được giữ','Delete Game Data':'Xóa dữ liệu trò chơi','Permanently delete saves, states, cheats and backups':'Xóa vĩnh viễn bản lưu, save state, cheat và bản sao lưu','Save state':'Save state','Rename':'Đổi tên',"Change this save state's display name":'Đổi tên hiển thị của save state này','Delete':'Xóa','Permanently remove this save state':'Xóa vĩnh viễn save state này','Edit ROM name':'Đổi tên ROM','Change the title shown in Retra. The ROM file name is not changed.':'Đổi tiêu đề hiển thị trong Retra. Tên tệp ROM sẽ không thay đổi.','Rename save state':'Đổi tên save state','This name is shown in Recent saves and Menu → Load state.':'Tên này sẽ hiển thị trong Bản lưu gần đây và Menu → Tải state.','Confirm':'Xác nhận','Are you sure?':'Bạn có chắc không?',
    'A new Retra version is available':'Đã có phiên bản Retra mới','Update Retra to get the latest improvements.':'Cập nhật Retra để nhận các cải tiến mới nhất.','Later':'Để sau','Update':'Cập nhật','Landscape':'Ngang','Reverse landscape':'Ngang đảo chiều','Portrait':'Dọc','System default':'Theo hệ thống','Performance':'Hiệu năng','Compatibility':'Tương thích','None':'Không','Import saves':'Nhập bản lưu','Due to an Android update, Retra no longer has access to the save folder created by older versions.':'Do thay đổi của Android, Retra không còn quyền truy cập thư mục lưu do phiên bản cũ tạo.','Please allow access by selecting the':'Vui lòng cấp quyền truy cập bằng cách chọn thư mục','folder. Your save data will then be automatically imported to the new location.':'Sau đó dữ liệu lưu sẽ được tự động nhập sang vị trí mới.','SELECT FOLDER':'CHỌN THƯ MỤC','SKIP':'BỎ QUA',
    '1 selected':'Đã chọn 1','Tap ROMs to select more':'Chạm ROM để chọn thêm','Favourite':'Yêu thích','Keep saves and game data':'Giữ bản lưu và dữ liệu trò chơi','Set categories':'Đặt danh mục','Apply categories to selected ROMs.':'Áp dụng danh mục cho các ROM đã chọn.','Sort':'Sắp xếp','Display':'Hiển thị','Library sort and display':'Sắp xếp và hiển thị thư viện','Alphabetically':'Theo bảng chữ cái','Playtime':'Thời gian chơi','Last played':'Lần chơi gần nhất','Date added':'Ngày thêm','Random':'Ngẫu nhiên','Tap the selected sort again to reverse its order. Tap Random again to reshuffle.':'Chạm lại kiểu sắp xếp đang chọn để đảo thứ tự. Chạm lại Ngẫu nhiên để xáo lại.','Display mode':'Chế độ hiển thị','Compact grid':'Lưới gọn','Comfortable grid':'Lưới thoáng','Cover-only grid':'Chỉ ảnh bìa','List':'Danh sách','Items per row':'Số mục mỗi hàng','Grid modes only':'Chỉ áp dụng cho chế độ lưới',
    'Back':'Quay lại','Add ROM':'Thêm ROM','Library options':'Tùy chọn thư viện','Items per row':'Số mục mỗi hàng'
  };

  const id = {
    'Library':'Pustaka','Refresh library':'Segarkan pustaka','History':'Riwayat','More':'Lainnya','Default':'Bawaan',
    'No ROMs in your library':'Belum ada ROM di pustaka','Tap + to add a ROM or ROM hack file from your device.':'Ketuk + untuk menambahkan file ROM atau ROM hack dari perangkat.',
    'Edit name':'Ubah nama','Change background':'Ganti latar','Change Cover':'Ganti sampul','Local file':'File lokal','ROM file':'File ROM','Ready':'Siap','Local Library':'Pustaka lokal','In library':'Di pustaka','Resume':'Lanjutkan','Recent saves':'Simpanan terbaru','0 entries':'0 entri',
    'Filter':'Filter','Set as default':'Jadikan bawaan',"Remember this ROM's current categories":'Ingat kategori ROM ini saat ini','Reset':'Atur ulang',"Return this ROM to its saved default":'Kembalikan ROM ini ke kategori bawaan yang tersimpan','Add to categories':'Tambahkan ke kategori','Select where this ROM should appear in your Library.':'Pilih tempat ROM ini akan tampil di Pustaka.','No categories yet. Create one in More → Categories.':'Belum ada kategori. Buat satu di Lainnya → Kategori.','Close':'Tutup','Save':'Simpan','No recent history yet.':'Belum ada riwayat terbaru.',
    'Incognito mode':'Mode samaran','Pauses playing history':'Menjeda pencatatan riwayat bermain','Statistics':'Statistik','Categories':'Kategori','Color Style':'Gaya warna','Classic':'Klasik','Data and Storage':'Data dan Penyimpanan','Backup, restore, storage':'Cadangan, pemulihan, penyimpanan','Settings':'Pengaturan','Support Us':'Dukung Kami','About':'Tentang','Help':'Bantuan',
    'Choose the color vibe used while playing. This changes only the game image, not the Retra interface.':'Pilih nuansa warna saat bermain. Pengaturan ini hanya mengubah gambar permainan, bukan antarmuka Retra.','Original balanced retro colors':'Warna retro asli yang seimbang','Vivid':'Cerah','Brighter, richer and more punchy':'Lebih terang, kaya, dan tegas','Warm':'Hangat','Softer golden retro tone':'Nuansa retro keemasan yang lebih lembut','Muted':'Lembut','Softer color and gentler contrast':'Warna lebih lembut dengan kontras yang halus','Classic uses the provided reference image as the baseline look.':'Klasik menggunakan gambar referensi sebagai tampilan dasar.',
    'Storage':'Penyimpanan','Retra storage':'Penyimpanan Retra','Open the Retra app folder in Android Files':'Buka folder Retra di aplikasi File Android','Storage usage':'Penggunaan penyimpanan','Checking storage…':'Memeriksa penyimpanan…',"Retra uses your phone's internal storage":'Retra menggunakan penyimpanan internal ponsel','Backup and restore':'Cadangkan dan pulihkan','Create backup':'Buat cadangan','Choose what to include, then save a .retra file anywhere':'Pilih data yang ingin disertakan, lalu simpan file .retra di mana saja','Restore backup':'Pulihkan cadangan','Restore data from an existing Retra .retra backup':'Pulihkan data dari cadangan Retra .retra yang sudah ada','Restore from Google Drive':'Pulihkan dari Google Drive','Recover saves, library, statistics and settings after reinstalling Retra':'Pulihkan simpanan, pustaka, statistik, dan pengaturan setelah memasang ulang Retra',
    'Retra backups do not include ROM or BIOS files. Save files, save states, cheats, layouts, artwork, library metadata, statistics, and portable app settings can be recovered separately.':'Cadangan Retra tidak menyertakan file ROM atau BIOS. File simpanan, save state, cheat, tata letak, gambar, metadata pustaka, statistik, dan pengaturan portabel dapat dipulihkan secara terpisah.','Google Drive':'Google Drive','Automatic Google Drive backup':'Cadangan Google Drive otomatis','Protect saves, statistics and settings across reinstalls and devices':'Lindungi simpanan, statistik, dan pengaturan saat memasang ulang atau berganti perangkat','Backup Now':'Cadangkan Sekarang','Upload a validated backup directly to My Drive/Retra Backups':'Unggah cadangan yang sudah divalidasi langsung ke My Drive/Retra Backups','Google Drive settings':'Pengaturan Google Drive','Connect a Google account to manage Drive backup':'Hubungkan akun Google untuk mengelola cadangan Drive',
    'Choose what you want to include. The backup will be saved as a single':'Pilih data yang ingin disertakan. Cadangan akan disimpan sebagai satu','file.':'file.','Game data':'Data permainan','Game saves':'Simpanan permainan','Battery saves used by your games':'Simpanan baterai yang digunakan permainan','Save states':'Save state','Manual, quick, and automatic save states':'Save state manual, cepat, dan otomatis','Cheats':'Cheat','Saved cheat codes and enabled-state data':'Kode cheat tersimpan dan status aktif/nonaktif','Library and metadata':'Pustaka dan metadata','Favorites, categories, playtime, and library metadata':'Favorit, kategori, waktu bermain, dan metadata pustaka','Controller layouts':'Tata letak kontrol','Screen Editor and controller positioning':'Editor Layar dan posisi kontrol','Artwork':'Gambar','Downloaded covers and backgrounds':'Sampul dan latar yang diunduh','App settings':'Pengaturan aplikasi','Portable Retra settings; device-specific permissions are excluded':'Pengaturan Retra portabel; izin khusus perangkat tidak disertakan',
    'Appearance':'Tampilan','Theme, dark mode, accents':'Tema, mode gelap, warna aksen','Video':'Video','Renderer, scaling, frame rate':'Renderer, penskalaan, laju bingkai','Audio':'Audio','Volume, latency, synchronization':'Volume, latensi, sinkronisasi','Layouts':'Tata letak','Controls, buttons, screen placement':'Kontrol, tombol, posisi layar','Misc':'Lain-lain','Saves, screenshots, vibration, library':'Simpanan, tangkapan layar, getaran, pustaka','Advanced':'Lanjutan','Core options, BIOS, debugging':'Opsi core, BIOS, debugging','Fonts':'Font','Choose the Retra interface font':'Pilih font antarmuka Retra','Language':'Bahasa','Choose the Retra interface language':'Pilih bahasa antarmuka Retra','Retra version, licenses, information':'Versi Retra, lisensi, dan informasi',
    'Theme':'Tema','System':'Sistem','Light':'Terang','Dark':'Gelap','Pure black dark mode':'Mode gelap hitam pekat','Use a deeper black palette for OLED screens':'Gunakan warna hitam lebih pekat untuk layar OLED','Translucent mode':'Mode transparan','Add a soft glass effect across cards and panels':'Tambahkan efek kaca lembut pada kartu dan panel',
    'Stretch to fit screen':'Rentangkan agar memenuhi layar','Stretch video inside the saved game-screen frame':'Rentangkan video di dalam bingkai layar permainan yang tersimpan','Controller Opacity':'Transparansi kontrol','Adjust the transparency of on-screen game controls':'Atur transparansi kontrol permainan di layar','Screen size':'Ukuran layar','Preview and adjust in landscape mode':'Pratinjau dan atur dalam mode lanskap','Screen orientation':'Orientasi layar','Auto rotate':'Putar otomatis','Frameskip':'Lewati bingkai','0 = smoothest (~60 FPS); 1 = ~30 FPS. Leave at 0 unless the device struggles.':'0 = paling mulus (~60 FPS); 1 = ~30 FPS. Biarkan 0 kecuali perangkat kesulitan.','Hardware rendering':'Rendering perangkat keras','Use Android GPU compositing for the game screen':'Gunakan komposit GPU Android untuk layar permainan','Linear filtering':'Filter linear','Smooth pixels when scaling':'Haluskan piksel saat diskalakan','Video shader':'Shader video','Off • no extra GPU cost':'Mati • tanpa beban GPU tambahan',
    'Screen editor':'Editor layar','Portrait and landscape use separate layouts. Rotate the device to edit that orientation. Back and + appear only in this editor and disappear during gameplay. Drag controls to move them and use the scale handle to resize them.':'Potret dan lanskap memakai tata letak terpisah. Putar perangkat untuk mengedit orientasi tersebut. Tombol Kembali dan + hanya muncul di editor ini dan hilang saat bermain. Seret kontrol untuk memindahkan, lalu gunakan pegangan skala untuk mengubah ukurannya.','Tap or drag screen • Drag edges or resize handle • Hold for modes':'Ketuk atau seret layar • Seret tepi atau pegangan untuk mengubah ukuran • Tahan untuk memilih mode','Make fullscreen':'Penuhi layar','Make centered':'Tengahkan','Best Fit':'Paling pas','Best scaling (4x)':'Penskalaan terbaik (4x)','Break aspect ratio':'Lepas rasio aspek','Grid':'Kisi','Remove control':'Hapus kontrol','Add control':'Tambah kontrol','Button A':'Tombol A','Button B':'Tombol B','Quick load':'Muat cepat','Quick save':'Simpan cepat','Fast forward':'Percepat','Screenshot':'Tangkapan layar','Reset layout':'Atur ulang tata letak','Cancel':'Batal','Tip: tap a control to select it. A scale handle appears beside the selected control so you can resize it.':'Tips: ketuk kontrol untuk memilihnya. Pegangan skala akan muncul di samping agar ukurannya dapat diubah.',
    'Enable sound':'Aktifkan suara','Turn emulator audio on or off':'Nyalakan atau matikan audio emulator','Sound volume':'Volume suara','Master emulator volume':'Volume utama emulator','Sound frequency':'Frekuensi suara','High quality':'Kualitas tinggi','Balanced':'Seimbang','Lower CPU usage':'Penggunaan CPU lebih rendah','Standard GBA controls':'Kontrol GBA standar','New layout profile':'Profil tata letak baru','Name this controller layout. After you press OK, Retra will open the Screen Editor for this profile.':'Beri nama tata letak kontrol ini. Setelah menekan OK, Retra akan membuka Editor Layar untuk profil tersebut.',
    'Enable cheats':'Aktifkan cheat','Allow cheat codes while playing':'Izinkan kode cheat saat bermain','ROM patching':'Patch ROM','Auto IPS/UPS/BPS patching when a matching patch is available':'Terapkan patch IPS/UPS/BPS otomatis jika tersedia patch yang cocok','Automatic game artwork':'Gambar permainan otomatis','Find and cache matching covers in the background':'Cari dan simpan sampul yang cocok di latar belakang','Artwork on Wi-Fi only':'Gambar hanya melalui Wi‑Fi','Reduce mobile-data use for automatic cover downloads':'Kurangi penggunaan data seluler untuk unduhan sampul otomatis','Retry missing artwork':'Coba lagi gambar yang hilang','Search again for ROMs that still use fallback colors':'Cari ulang gambar untuk ROM yang masih memakai warna cadangan','Auto save & load':'Simpan & muat otomatis','Quick-save on background/exit and keep automatic resume available on next launch':'Simpan cepat saat aplikasi ke latar/keluar dan tetap sediakan lanjut otomatis saat dibuka lagi','Emulation speed':'Kecepatan emulasi','Speed button':'Tombol kecepatan','Press to toggle':'Tekan untuk aktif/nonaktif','Confirm on close/reset':'Konfirmasi saat tutup/atur ulang','Ask before closing or resetting a game':'Tanyakan sebelum menutup atau mengatur ulang permainan','Full screen mode':'Mode layar penuh','Make games fullscreen by hiding system bars':'Jadikan permainan layar penuh dengan menyembunyikan bilah sistem','Immersive mode':'Mode imersif','Hide gesture/navigation bars while a game is running':'Sembunyikan bilah gestur/navigasi saat permainan berjalan','Open app folder':'Buka folder aplikasi','Open Retra storage in Android Files':'Buka penyimpanan Retra di aplikasi File Android',
    'CPU profile':'Profil CPU','Automatic':'Otomatis','Use BIOS':'Gunakan BIOS','Enable low-level BIOS emulation':'Aktifkan emulasi BIOS tingkat rendah','Boot BIOS':'Boot BIOS','Show BIOS screen on game start':'Tampilkan layar BIOS saat permainan dimulai','BIOS file':'File BIOS','No BIOS selected':'Belum ada BIOS dipilih','Cartridge save type':'Jenis simpanan cartridge','Link sync check':'Pemeriksaan sinkronisasi Link','Remote Link state-integrity interval; smaller values check more often':'Interval pemeriksaan integritas Remote Link; nilai lebih kecil memeriksa lebih sering','Speed optimization':'Optimasi kecepatan','Enable mGBA idle-loop optimization':'Aktifkan optimasi idle-loop mGBA','Mosaic effect':'Efek mosaik','Enable GBA mosaic rendering':'Aktifkan rendering mosaik GBA','Reset advanced settings':'Atur ulang pengaturan lanjutan','Restore advanced options to defaults':'Kembalikan opsi lanjutan ke bawaan',
    'Poppins':'Poppins','Alternative':'Alternatif','Inter':'Inter','Default • clean and highly readable':'Bawaan • bersih dan sangat mudah dibaca','Clean and highly readable':'Bersih dan sangat mudah dibaca','Manrope':'Manrope','Modern rounded sans-serif':'Sans-serif bulat modern','DM Sans':'DM Sans','Balanced and compact':'Seimbang dan ringkas','English':'Inggris','Vietnamese interface':'Antarmuka bahasa Vietnam yang alami','Indonesian interface':'Antarmuka Bahasa Indonesia yang alami',
    'Version':'Versi','Check for updates':'Periksa pembaruan','Automatically checked in the background':'Diperiksa otomatis di latar belakang','Retra is an independent emulator frontend. No copyrighted game software or ROM files are included with this product.':'Retra adalah antarmuka emulator independen. Produk ini tidak menyertakan perangkat lunak permainan berhak cipta atau file ROM.','Privacy Policy':'Kebijakan Privasi','Retra Privacy Policy':'Kebijakan Privasi Retra','Local game data.':'Data permainan lokal.','Network features.':'Fitur jaringan.','Google Drive.':'Google Drive.','No advertising or analytics SDK.':'Tanpa SDK iklan atau analitik.','Permissions.':'Izin.','Data deletion.':'Penghapusan data.','Uninstall and reinstall.':'Copot dan pasang ulang.','Policy updates.':'Pembaruan kebijakan.',
    'Edit categories':'Edit kategori','You have no categories. Tap the Add button to create one for organizing your library.':'Anda belum memiliki kategori. Ketuk Tambah untuk membuat kategori dan menata pustaka.','Add':'Tambah','Add category':'Tambah kategori','Create a category for organizing ROMs in your Library.':'Buat kategori untuk mengatur ROM di Pustaka.','Overview':'Ringkasan','Total playtime':'Total waktu bermain','Completed games':'Permainan selesai','Entries':'Entri','Started':'Dimulai','Favorites':'Favorit','Recently played':'Baru dimainkan','Saves':'Simpanan','Battery saves':'Simpanan baterai','Backups':'Cadangan','Systems':'Sistem','Playtime by ROM':'Waktu bermain per ROM','No playtime data yet.':'Belum ada data waktu bermain.',
    'Support Retra':'Dukung Retra','If you enjoy using Retra, you can support development through Buy Me a Coffee.':'Jika Anda menikmati Retra, Anda dapat mendukung pengembangannya melalui Buy Me a Coffee.','Need help?':'Butuh bantuan?','Use Help for setup guides, controller help, save import instructions, and troubleshooting tips for ROM scanning and emulator settings.':'Gunakan Bantuan untuk panduan penyiapan, bantuan kontrol, petunjuk impor simpanan, serta tips pemecahan masalah pemindaian ROM dan pengaturan emulator.','Open help center':'Buka pusat bantuan',
    'Add to Favourites':'Tambahkan ke Favorit','Keep this ROM at the front of your Library':'Pertahankan ROM ini di bagian depan Pustaka','Remove from Library':'Hapus dari Pustaka','Hide this ROM • saves and game data are kept':'Sembunyikan ROM ini • simpanan dan data permainan tetap disimpan','Delete Game Data':'Hapus Data Permainan','Permanently delete saves, states, cheats and backups':'Hapus permanen simpanan, state, cheat, dan cadangan','Save state':'Save state','Rename':'Ubah nama',"Change this save state's display name":'Ubah nama tampilan save state ini','Delete':'Hapus','Permanently remove this save state':'Hapus save state ini secara permanen','Edit ROM name':'Ubah nama ROM','Change the title shown in Retra. The ROM file name is not changed.':'Ubah judul yang tampil di Retra. Nama file ROM tidak akan berubah.','Rename save state':'Ubah nama save state','This name is shown in Recent saves and Menu → Load state.':'Nama ini tampil di Simpanan terbaru dan Menu → Muat state.','Confirm':'Konfirmasi','Are you sure?':'Apakah Anda yakin?',
    'A new Retra version is available':'Versi Retra baru tersedia','Update Retra to get the latest improvements.':'Perbarui Retra untuk mendapatkan peningkatan terbaru.','Later':'Nanti','Update':'Perbarui','Landscape':'Lanskap','Reverse landscape':'Lanskap terbalik','Portrait':'Potret','System default':'Bawaan sistem','Performance':'Performa','Compatibility':'Kompatibilitas','None':'Tidak ada','Import saves':'Impor simpanan','Due to an Android update, Retra no longer has access to the save folder created by older versions.':'Karena perubahan Android, Retra tidak lagi memiliki akses ke folder simpanan yang dibuat versi lama.','Please allow access by selecting the':'Izinkan akses dengan memilih folder','folder. Your save data will then be automatically imported to the new location.':'Setelah itu data simpanan akan diimpor otomatis ke lokasi baru.','SELECT FOLDER':'PILIH FOLDER','SKIP':'LEWATI',
    '1 selected':'1 dipilih','Tap ROMs to select more':'Ketuk ROM untuk memilih lebih banyak','Favourite':'Favorit','Keep saves and game data':'Pertahankan simpanan dan data permainan','Set categories':'Atur kategori','Apply categories to selected ROMs.':'Terapkan kategori ke ROM yang dipilih.','Sort':'Urutkan','Display':'Tampilan','Library sort and display':'Pengurutan dan tampilan pustaka','Alphabetically':'Menurut abjad','Playtime':'Waktu bermain','Last played':'Terakhir dimainkan','Date added':'Tanggal ditambahkan','Random':'Acak','Tap the selected sort again to reverse its order. Tap Random again to reshuffle.':'Ketuk lagi metode pengurutan yang dipilih untuk membalik urutannya. Ketuk Acak lagi untuk mengacak ulang.','Display mode':'Mode tampilan','Compact grid':'Kisi ringkas','Comfortable grid':'Kisi nyaman','Cover-only grid':'Kisi sampul saja','List':'Daftar','Items per row':'Item per baris','Grid modes only':'Hanya untuk mode kisi',
    'Back':'Kembali','Add ROM':'Tambah ROM','Library options':'Opsi pustaka'
  };

  const dictionaries = { vi, id };
  const messageFormats = {
    en: { fontSelected: '{font} font selected', languageSelected: 'Language changed to {language}' },
    vi: { fontSelected: 'Đã chọn phông chữ {font}', languageSelected: 'Đã đổi ngôn ngữ sang {language}' },
    id: { fontSelected: 'Font {font} dipilih', languageSelected: 'Bahasa diubah ke {language}' }
  };

  const trackedText = new Set();
  const textOriginal = new WeakMap();
  const trackedAttributes = [];
  const attrOriginal = new WeakMap();
  let currentLanguage = 'en';
  let applying = false;

  const userContentSelector = [
    '#libraryGrid', '#detailTitle', '#detailAuthor', '#detailStudio', '#detailDescription', '#sheetRomTitle',
    '#historyList', '#detailEntries', '#playtimeList', '#multiCategoryList', '#categoryList', '#layoutProfileList',
    '#romNameInput', '#saveRenameInput', '#categoryNameInput', '[data-user-content="true"]'
  ].join(',');

  function nativeGet(key){
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.getUiPreference === 'function') {
        return String(window.AndroidBridge.getUiPreference(String(key)) || '');
      }
    } catch (_) {}
    return '';
  }

  function nativeSet(key, value){
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.setUiPreference === 'function') {
        return Boolean(window.AndroidBridge.setUiPreference(String(key), String(value)));
      }
    } catch (_) {}
    return false;
  }

  function normalizeLanguage(value){
    const raw = String(value || '').trim().toLowerCase();
    if (raw === 'vietnamese' || raw === 'vi-vn') return 'vi';
    if (raw === 'indonesian' || raw === 'bahasa indonesia' || raw === 'in-id') return 'id';
    return SUPPORTED.has(raw) ? raw : 'en';
  }

  function savedLanguage(){
    return normalizeLanguage(nativeGet('language') || localStorage.getItem(STORAGE_KEY) || 'en');
  }

  function translateExact(english, language = currentLanguage){
    if (language === 'en') return english;
    return dictionaries[language]?.[english] || english;
  }

  function shouldSkip(node){
    const element = node?.nodeType === Node.ELEMENT_NODE ? node : node?.parentElement;
    return Boolean(element?.closest?.(userContentSelector));
  }

  function registerTextNode(node){
    if (!node || node.nodeType !== Node.TEXT_NODE || shouldSkip(node)) return;
    const raw = node.nodeValue || '';
    const trimmed = raw.trim();
    if (!trimmed) return;
    const known = Object.prototype.hasOwnProperty.call(vi, trimmed) || Object.prototype.hasOwnProperty.call(id, trimmed);
    if (!known) return;
    if (!textOriginal.has(node)) {
      textOriginal.set(node, { english: trimmed, prefix: raw.slice(0, raw.indexOf(trimmed)), suffix: raw.slice(raw.indexOf(trimmed) + trimmed.length) });
      trackedText.add(node);
    }
    renderTextNode(node);
  }

  function renderTextNode(node){
    const info = textOriginal.get(node);
    if (!info || !node.isConnected) return;
    const next = `${info.prefix}${translateExact(info.english)}${info.suffix}`;
    if (node.nodeValue !== next) node.nodeValue = next;
  }

  function registerAttribute(element, attr){
    if (!element || shouldSkip(element)) return;
    const value = String(element.getAttribute(attr) || '').trim();
    if (!value) return;
    const known = Object.prototype.hasOwnProperty.call(vi, value) || Object.prototype.hasOwnProperty.call(id, value);
    if (!known) return;
    let map = attrOriginal.get(element);
    if (!map) { map = {}; attrOriginal.set(element, map); }
    if (!map[attr]) {
      map[attr] = value;
      trackedAttributes.push({ element, attr });
    }
    const next = translateExact(map[attr]);
    if (element.getAttribute(attr) !== next) element.setAttribute(attr, next);
  }

  function scan(root = document.body){
    if (!root) return;
    if (root.nodeType === Node.TEXT_NODE) {
      registerTextNode(root);
      return;
    }
    if (root.nodeType !== Node.ELEMENT_NODE && root.nodeType !== Node.DOCUMENT_NODE) return;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) registerTextNode(node);
    const elements = root.matches?.('[aria-label],[title],[placeholder]') ? [root] : [];
    elements.push(...root.querySelectorAll?.('[aria-label],[title],[placeholder]') || []);
    elements.forEach(el => ['aria-label','title','placeholder'].forEach(attr => {
      if (el.hasAttribute(attr)) registerAttribute(el, attr);
    }));
  }

  function refreshTracked(){
    applying = true;
    trackedText.forEach(node => renderTextNode(node));
    trackedAttributes.forEach(({ element, attr }) => {
      if (!element?.isConnected) return;
      const original = attrOriginal.get(element)?.[attr];
      if (original) element.setAttribute(attr, translateExact(original));
    });
    applying = false;
  }

  function updateLanguageButtons(){
    document.querySelectorAll('.language-option').forEach(option => {
      const active = option.dataset.languageCode === currentLanguage;
      option.classList.toggle('active', active);
      option.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
  }

  function applyLanguage(language, { persist = false, notify = false } = {}){
    currentLanguage = normalizeLanguage(language);
    document.documentElement.lang = currentLanguage;
    document.body.dataset.uiLanguage = currentLanguage;
    if (persist) {
      localStorage.setItem(STORAGE_KEY, currentLanguage);
      nativeSet('language', currentLanguage);
    }
    refreshTracked();
    scan(document.body);
    updateLanguageButtons();
    if (notify && typeof window.showToast === 'function') {
      const languageName = currentLanguage === 'vi' ? 'Tiếng Việt' : currentLanguage === 'id' ? 'Bahasa Indonesia' : 'English';
      window.showToast(format('languageSelected', { language: languageName }));
    }
    window.dispatchEvent(new CustomEvent('retra-language-changed', { detail: { language: currentLanguage } }));
  }

  function format(key, values = {}){
    const template = messageFormats[currentLanguage]?.[key] || messageFormats.en[key] || key;
    return template.replace(/\{(\w+)\}/g, (_, name) => String(values[name] ?? ''));
  }

  function translateMessage(message){
    const text = String(message ?? '');
    const exact = translateExact(text);
    if (exact !== text) return exact;
    if (currentLanguage === 'vi') {
      let m = text.match(/^Renamed to (.+)$/); if (m) return `Đã đổi tên thành ${m[1]}`;
      m = text.match(/^(.+) font selected$/); if (m) return `Đã chọn phông chữ ${m[1]}`;
      m = text.match(/^Screen orientation: (.+)$/); if (m) return `Hướng màn hình: ${m[1]}`;
      m = text.match(/^Emulation speed: (.+)$/); if (m) return `Tốc độ giả lập: ${m[1]}`;
      m = text.match(/^Sound frequency: (.+)$/); if (m) return `Tần số âm thanh: ${m[1]}`;
      m = text.match(/^(.+) added$/); if (m) return `Đã thêm ${m[1]}`;
      m = text.match(/^(.+) removed$/); if (m) return `Đã xóa ${m[1]}`;
    } else if (currentLanguage === 'id') {
      let m = text.match(/^Renamed to (.+)$/); if (m) return `Diubah menjadi ${m[1]}`;
      m = text.match(/^(.+) font selected$/); if (m) return `Font ${m[1]} dipilih`;
      m = text.match(/^Screen orientation: (.+)$/); if (m) return `Orientasi layar: ${m[1]}`;
      m = text.match(/^Emulation speed: (.+)$/); if (m) return `Kecepatan emulasi: ${m[1]}`;
      m = text.match(/^Sound frequency: (.+)$/); if (m) return `Frekuensi suara: ${m[1]}`;
      m = text.match(/^(.+) added$/); if (m) return `${m[1]} ditambahkan`;
      m = text.match(/^(.+) removed$/); if (m) return `${m[1]} dihapus`;
    }
    return text;
  }

  const observer = new MutationObserver(records => {
    if (applying) return;
    records.forEach(record => {
      if (record.type === 'childList') {
        record.addedNodes.forEach(node => scan(node));
      } else if (record.type === 'characterData') {
        registerTextNode(record.target);
      } else if (record.type === 'attributes' && ['aria-label','title','placeholder'].includes(record.attributeName)) {
        registerAttribute(record.target, record.attributeName);
      }
    });
  });

  window.retraI18n = { t: translateExact, format, translateMessage, applyLanguage, getLanguage: () => currentLanguage };

  scan(document.body);
  currentLanguage = savedLanguage();
  applyLanguage(currentLanguage, { persist: !nativeGet('language') });
  observer.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ['aria-label','title','placeholder'] });

  document.querySelectorAll('.language-option').forEach(option => {
    option.addEventListener('click', () => applyLanguage(option.dataset.languageCode, { persist: true, notify: true }));
  });
})();
