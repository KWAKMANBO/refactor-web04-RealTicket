-- 구역 좌석 비트맵을 초기화한다.
-- KEYS[1] 좌석 비트맵  KEYS[2] 좌석 비트맵 길이
-- ARGV[1] "1"/"0" 로 된 좌석 문자열 (1: 예약 가능)
local bits = ARGV[1]
redis.call('DEL', KEYS[1])
for i = 1, string.len(bits) do
  redis.call('SETBIT', KEYS[1], i - 1, tonumber(string.sub(bits, i, i)))
end
redis.call('SET', KEYS[2], string.len(bits))
return 1
