-- 입장 허가 후 기한 안에 좌석 화면에 들어오지 않은 사용자를 정리한다.
-- KEYS[1] entering:{e}  KEYS[2] entering:{e}:amounts
-- ARGV[1] 이 시각(ms) 이전에 허가된 세션을 만료
-- 반환: 만료된 sid 목록
local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
if #expired > 0 then
  redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
  redis.call('HDEL', KEYS[2], unpack(expired))
end
return expired
