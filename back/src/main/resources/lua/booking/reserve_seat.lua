-- 좌석 하나를 선점한다. 인원 수 확인, 좌석 상태 확인, 선점 기록을 한 번에 처리한다.
-- KEYS[1] 좌석 비트맵  KEYS[2] 좌석 비트맵 길이  KEYS[3] in-booking 세션 Hash  KEYS[4] 세션의 선점 좌석 Set
-- ARGV[1] seatIndex  ARGV[2] 좌석 멤버 "sectionIndex:seatIndex"
-- 반환: 1 성공, 0 이미 예약된 좌석, -1 좌석 선택 중 아님, -2 이미 예매 완료, -3 인원 초과, -4 없는 좌석
if redis.call('EXISTS', KEYS[3]) == 0 then return -1 end
if redis.call('HGET', KEYS[3], 'saved') == '1' then return -2 end
local amount = tonumber(redis.call('HGET', KEYS[3], 'bookingAmount') or '0')
if redis.call('SCARD', KEYS[4]) >= amount then return -3 end
local len = tonumber(redis.call('GET', KEYS[2]) or '-1')
local index = tonumber(ARGV[1])
if index < 0 or index >= len then return -4 end
if redis.call('GETBIT', KEYS[1], index) == 0 then return 0 end
redis.call('SETBIT', KEYS[1], index, 0)
redis.call('SADD', KEYS[4], ARGV[2])
return 1
