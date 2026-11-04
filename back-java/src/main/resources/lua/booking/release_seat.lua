-- 내가 선점한 좌석 하나를 취소한다.
-- KEYS[1] 좌석 비트맵  KEYS[2] in-booking 세션 Hash  KEYS[3] 세션의 선점 좌석 Set
-- ARGV[1] seatIndex  ARGV[2] 좌석 멤버
-- 반환: 1 성공, -1 좌석 선택 중 아님, -2 이미 예매 완료, -5 선점하지 않은 좌석
if redis.call('EXISTS', KEYS[2]) == 0 then return -1 end
if redis.call('HGET', KEYS[2], 'saved') == '1' then return -2 end
if redis.call('SISMEMBER', KEYS[3], ARGV[2]) == 0 then return -5 end
redis.call('SETBIT', KEYS[1], tonumber(ARGV[1]), 1)
redis.call('SREM', KEYS[3], ARGV[2])
return 1
