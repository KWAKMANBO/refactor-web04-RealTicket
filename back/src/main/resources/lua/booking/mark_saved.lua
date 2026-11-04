-- 예매 저장을 시작한다는 표시. 중복 예매 요청을 막는다.
-- KEYS[1] in-booking 세션 Hash
-- 반환: 1 표시함, 0 이미 저장됨(또는 저장 중), -1 좌석 선택 중 아님
if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
if redis.call('HGET', KEYS[1], 'saved') == '1' then return 0 end
redis.call('HSET', KEYS[1], 'saved', '1')
return 1
