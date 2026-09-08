-- KEYS[1]: 락 키
-- ARGV[1]: 이 요청이 락을 걸 때 넣은 토큰
-- 내가 건 락인지 확인하고 지운다(compare-and-delete) — TTL 만료 후 다른 요청이 새로
-- 락을 잡았는데 내가 뒤늦게 그냥 DEL을 날리면 그 요청의 락을 잘못 지울 수 있다.
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
else
    return 0
end
