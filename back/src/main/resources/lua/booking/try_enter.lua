-- 예매 입장 요청. 자리가 있고 대기열이 비어 있으면 바로 입장, 아니면 대기열 맨 뒤에 넣는다.
-- KEYS[1] waiting-queue:{e}  KEYS[2] waiting-queue:{e}:order  KEYS[3] waiting-queue:{e}:orders
-- KEYS[4] entering:{e}        KEYS[5] in-booking:{e}:sessions  KEYS[6] in-booking:{e}:max-size
-- ARGV[1] sid  ARGV[2] 현재 시각(ms)
-- 반환: -1 이미 좌석 선택 중, -2 이미 입장 허가됨, 0 새로 입장, 양수 대기 번호
local sid = ARGV[1]
if redis.call('SISMEMBER', KEYS[5], sid) == 1 then return -1 end
if redis.call('ZSCORE', KEYS[4], sid) then return -2 end
local existingOrder = redis.call('HGET', KEYS[3], sid)
if existingOrder then return tonumber(existingOrder) end

local maxSize = tonumber(redis.call('GET', KEYS[6]) or '0')
local count = redis.call('SCARD', KEYS[5]) + redis.call('ZCARD', KEYS[4])
if redis.call('LLEN', KEYS[1]) == 0 and count < maxSize then
  redis.call('ZADD', KEYS[4], ARGV[2], sid)
  return 0
end

local order = redis.call('INCR', KEYS[2])
redis.call('RPUSH', KEYS[1], sid)
redis.call('HSET', KEYS[3], sid, order)
return order
