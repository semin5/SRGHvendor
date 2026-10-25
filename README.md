# 외부 업체

Spring Boot API and React UI based on the existing account-list project.

- URL: `/vendor/`; listener: port `3017` on each HA node and VIP `192.168.1.76`.
- Database: `srghvendor` through the MySQL 3307 VIP.
- Tables: `work_sites`, `vendor_info`, `maintenance_vendors`, `video_monthly_inspections`.
- Imported workbook rows retain their original Excel row number as `source_row`.
- 업체 정보 중 `누리보이스(전화기)` 행에서만 출력 버튼을 사용할 수 있습니다. `pdf/nuri-voice-as-template.pdf` 원본 양식에 기기명, MAC, 증상을 기입한 1페이지 PDF를 생성합니다. 미리보기와 인쇄는 브라우저에서 처리하며 입력값은 DB에 저장하지 않습니다.
- Runtime database credentials belong in `/etc/SRGH_IT/vendor.env`, not in this project.

The work-site sheet contains plaintext passwords and other credentials. The list API omits the password and remarks fields, but the detail API intentionally exposes them to match the account-list workflow. Keep network access limited to trusted users and never log full records.
