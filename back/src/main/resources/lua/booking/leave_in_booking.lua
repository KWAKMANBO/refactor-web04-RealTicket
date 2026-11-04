-- 좌석 선택 화면을 떠난 사용자를 정리한다. 예매를 완료하지 않았다면 선점 좌석을 풀어 준다.
-- KEYS[1] in-booking:{e}:sessions  KEYS[2] in-booking 세션 Hash  KEYS[3] 세션의 선점 좌석 Set
-- ARGV[1] sid  ARGV[2] 좌석 키 접두사 "event:{e}:section:"
-- 반환: 풀어 준 좌석 수, -1 좌석 선택 중이 아니었음
if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 0 then return -1 end
local released = 0
if redis.call('HGET', KEYS[2], 'saved') ~= '1' then
  local seats = redis.call('SMEMBERS', KEYS[3])
  for _, member in ipairs(seats) do
    local sep = string.find(member, ':')
    local section = string.sub(member, 1, sep - 1)
    local index = tonumber(string.sub(member, sep + 1))
    redis.call('SETBIT', ARGV[2] .. section .. ':seats', index, 1)
  end
  released = #seats
end
redis.call('DEL', KEYS[2], KEYS[3])
redis.call('SREM', KEYS[1], ARGV[1])
return released
