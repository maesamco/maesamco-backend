-- KEYS[1]: rate-limit key
-- ARGV[1]: window seconds
-- 반환값: 이번 요청까지 포함한 현재 카운트
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return current
