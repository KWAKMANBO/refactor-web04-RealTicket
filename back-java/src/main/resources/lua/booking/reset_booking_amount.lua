-- 좌석 선택 중 인원 수를 바꾼다. 이미 선점한 좌석은 모두 풀어 준다.
-- KEYS[1] in-booking 세션 Hash  KEYS[2] 세션의 선점 좌석 Set
-- ARGV[1] 새 인원 수  ARGV[2] 좌석 키 접두사 "event:{e}:section:"
-- 반환: 풀어 준 좌석 수, -1 좌석 선택 중 아님, -2 이미 예매 완료
if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
if redis.call('HGET', KEYS[1], 'saved') == '1' then return -2 end
local seats = redis.call('SMEMBERS', KEYS[2])
for _, member in ipairs(seats) do
  local sep = string.find(member, ':')
  local section = string.sub(member, 1, sep - 1)
  local index = tonumber(string.sub(member, sep + 1))
  redis.call('SETBIT', ARGV[2] .. section .. ':seats', index, 1)
end
redis.call('DEL', KEYS[2])
redis.call('HSET', KEYS[1], 'bookingAmount', ARGV[1])
return #seats
