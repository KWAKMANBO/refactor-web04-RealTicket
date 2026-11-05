-- [비교 1] 좌석 배치 조회 시 DB 가 실제로 읽는 행 수
--   docker compose exec -T mysql mysql -uroot -proot real_ticket < sql/layout-explain.sql
-- @place_id 를 seed 결과의 placeId 로 바꿔서 실행한다.
SET @place_id = 1;

SELECT '--- 반정규화 (운영): 구역 수만큼 행을 읽는다 ---' AS '';
EXPLAIN ANALYZE
SELECT s.id, s.name, s.col_len, s.seats
FROM sections s
WHERE s.place_id = @place_id;

SELECT '--- 좌석 1개 = 1행: 좌석 수만큼 행을 읽고 JOIN 한다 ---' AS '';
EXPLAIN ANALYZE
SELECT s.id, s.name, s.col_len, bs.is_seat
FROM sections s
JOIN bench_seats bs ON bs.section_id = s.id
WHERE s.place_id = @place_id
ORDER BY s.id, bs.seat_index;

SELECT '--- 저장 공간 ---' AS '';
SELECT table_name,
       table_rows,
       ROUND((data_length + index_length) / 1024, 1) AS size_kb
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('sections', 'bench_seats');
