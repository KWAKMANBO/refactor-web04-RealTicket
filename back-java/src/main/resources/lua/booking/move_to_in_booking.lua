-- 입장 허가된 사용자를 좌석 선택 중으로 옮긴다. 이미 좌석 선택 중이면 그대로 둔다.
-- KEYS[1] entering:{e}  KEYS[2] entering:{e}:amounts  KEYS[3] in-booking:{e}:sessions
-- KEYS[4] in-booking:{e}:session:{sid}
-- ARGV[1] sid
-- 반환: 1 옮김, 0 이미 좌석 선택 중, -1 입장 허가 없음(만료 등)
local sid = ARGV[1]
if redis.call('SISMEMBER', KEYS[3], sid) == 1 then return 0 end
if not redis.call('ZSCORE', KEYS[1], sid) then return -1 end
local amount = redis.call('HGET', KEYS[2], sid) or '0'
redis.call('ZREM', KEYS[1], sid)
redis.call('HDEL', KEYS[2], sid)
redis.call('SADD', KEYS[3], sid)
redis.call('HSET', KEYS[4], 'bookingAmount', amount, 'saved', '0')
return 1
