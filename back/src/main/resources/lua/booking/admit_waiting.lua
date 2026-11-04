-- 빈자리만큼 대기열 앞에서부터 입장시킨다.
-- KEYS[1] waiting-queue:{e}  KEYS[2] waiting-queue:{e}:orders  KEYS[3] entering:{e}
-- KEYS[4] in-booking:{e}:sessions  KEYS[5] in-booking:{e}:max-size
-- ARGV[1] 현재 시각(ms)
-- 반환: 입장시킨 sid 목록
local maxSize = tonumber(redis.call('GET', KEYS[5]) or '0')
local admitted = {}
while redis.call('SCARD', KEYS[4]) + redis.call('ZCARD', KEYS[3]) < maxSize do
  local sid = redis.call('LPOP', KEYS[1])
  if not sid then break end
  redis.call('HDEL', KEYS[2], sid)
  redis.call('ZADD', KEYS[3], ARGV[1], sid)
  table.insert(admitted, sid)
end
return admitted
