#!/usr/bin/env bash
#
# Mock Worker Rate Limit 탐색 스크립트
# 목적: mock worker가 429를 반환하는 정확한 기준(RPS, 버스트 등)을 파악
#
# 사용법:
#   chmod +x stress-test/mock-worker-rate-limit-test.sh
#   ./stress-test/mock-worker-rate-limit-test.sh
#
# 환경변수:
#   MOCK_WORKER_BASE_URL  (default: https://dev.realteeth.ai/mock)
#   MOCK_WORKER_API_KEY   (default: mock_1293d47ba4b74d95b3ae0e514d043e5f)

set -euo pipefail

BASE_URL="${MOCK_WORKER_BASE_URL:-https://dev.realteeth.ai/mock}"
API_KEY="${MOCK_WORKER_API_KEY:-mock_1293d47ba4b74d95b3ae0e514d043e5f}"
ENDPOINT="${BASE_URL}/process"

# 색상
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log() { echo -e "${CYAN}[$(date +%H:%M:%S)]${NC} $*"; }
ok()  { echo -e "${GREEN}[$(date +%H:%M:%S)]${NC} $*"; }
warn(){ echo -e "${YELLOW}[$(date +%H:%M:%S)]${NC} $*"; }
err() { echo -e "${RED}[$(date +%H:%M:%S)]${NC} $*"; }

# 단일 요청 전송 (status code 반환)
send_request() {
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$ENDPOINT" \
        -H "Content-Type: application/json" \
        -H "X-API-KEY: $API_KEY" \
        -d "{\"imageUrl\": \"https://example.com/test-$(uuidgen 2>/dev/null || echo $RANDOM).jpg\"}" \
        --connect-timeout 5 \
        --max-time 10)
    echo "$status"
}

# ─────────────────────────────────────────────
# 테스트 1: 단일 요청 연결 확인
# ─────────────────────────────────────────────
test_connectivity() {
    log "=== 테스트 1: 연결 확인 ==="
    local status
    status=$(send_request)
    if [[ "$status" == "200" ]]; then
        ok "연결 성공 (HTTP $status)"
    else
        err "연결 실패 (HTTP $status) - BASE_URL, API_KEY를 확인하세요"
        exit 1
    fi
    echo
}

# ─────────────────────────────────────────────
# 테스트 2: 버스트 테스트 (동시 요청)
# 동시에 N개 요청을 보내서 몇 개부터 429가 나오는지 확인
# ─────────────────────────────────────────────
test_burst() {
    local burst_sizes=(5 10 15 20 30 40 50 60 80 100)
    log "=== 테스트 2: 버스트 테스트 (동시 요청) ==="
    log "각 버스트 사이 10초 대기 (토큰 리필 대기)"
    echo

    for burst in "${burst_sizes[@]}"; do
        local tmpdir
        tmpdir=$(mktemp -d)

        log "버스트 크기: ${burst}개 동시 요청 전송 중..."

        # 병렬로 요청 전송
        for i in $(seq 1 "$burst"); do
            send_request > "$tmpdir/$i" &
        done
        wait

        # 결과 집계
        local total=0 ok_count=0 rate_limited=0 other=0
        for f in "$tmpdir"/*; do
            total=$((total + 1))
            local code
            code=$(cat "$f")
            case "$code" in
                200) ok_count=$((ok_count + 1)) ;;
                429) rate_limited=$((rate_limited + 1)) ;;
                *)   other=$((other + 1)) ;;
            esac
        done
        rm -rf "$tmpdir"

        if [[ $rate_limited -gt 0 ]]; then
            warn "  버스트=$burst → 200: $ok_count, 429: $rate_limited, 기타: $other"
        else
            ok "  버스트=$burst → 200: $ok_count, 429: $rate_limited, 기타: $other"
        fi

        # 토큰 리필 대기
        if [[ $burst -lt 100 ]]; then
            sleep 10
        fi
    done
    echo
}

# ─────────────────────────────────────────────
# 테스트 3: 지속 RPS 테스트
# 일정 RPS로 요청을 보내서 429 비율 확인
# ─────────────────────────────────────────────
test_sustained_rps() {
    local rps_levels=(1 3 5 8 10 15 20 30)
    local duration=10  # 각 레벨당 테스트 시간(초)

    log "=== 테스트 3: 지속 RPS 테스트 (각 ${duration}초) ==="
    log "각 레벨 사이 15초 대기 (토큰 리필 대기)"
    echo

    for rps in "${rps_levels[@]}"; do
        local interval
        # interval in ms between requests (using bc for float)
        interval=$(echo "scale=0; 1000 / $rps" | bc)

        local total=0 ok_count=0 rate_limited=0 other=0
        local start_time end_time
        start_time=$(date +%s)
        end_time=$((start_time + duration))

        log "RPS=$rps (요청 간격: ${interval}ms, 총 ${duration}초)..."

        while [[ $(date +%s) -lt $end_time ]]; do
            local status
            status=$(send_request)
            total=$((total + 1))
            case "$status" in
                200) ok_count=$((ok_count + 1)) ;;
                429) rate_limited=$((rate_limited + 1)) ;;
                *)   other=$((other + 1)) ;;
            esac

            # 간격 대기 (ms → sleep)
            if [[ $interval -gt 0 ]]; then
                sleep "$(echo "scale=3; $interval / 1000" | bc)"
            fi
        done

        local rate_pct=0
        if [[ $total -gt 0 ]]; then
            rate_pct=$(echo "scale=1; $rate_limited * 100 / $total" | bc)
        fi

        if [[ $rate_limited -gt 0 ]]; then
            warn "  RPS=$rps → 전송: $total, 200: $ok_count, 429: $rate_limited ($rate_pct%), 기타: $other"
        else
            ok "  RPS=$rps → 전송: $total, 200: $ok_count, 429: $rate_limited ($rate_pct%), 기타: $other"
        fi

        # 토큰 리필 대기
        sleep 15
    done
    echo
}

# ─────────────────────────────────────────────
# 테스트 4: 토큰 리필 속도 측정
# 버스트로 토큰 소진 → 일정 간격으로 요청하여 리필 속도 추정
# ─────────────────────────────────────────────
test_refill_rate() {
    log "=== 테스트 4: 토큰 리필 속도 측정 ==="
    log "Step 1: 50개 버스트로 토큰 소진..."

    local tmpdir
    tmpdir=$(mktemp -d)
    for i in $(seq 1 50); do
        send_request > "$tmpdir/$i" &
    done
    wait
    rm -rf "$tmpdir"

    log "Step 2: 1초 간격으로 요청하여 리필 확인 (30초간)..."
    echo

    local recovered_at=""
    for sec in $(seq 1 30); do
        local status
        status=$(send_request)
        if [[ "$status" == "200" ]]; then
            if [[ -z "$recovered_at" ]]; then
                recovered_at=$sec
                ok "  ${sec}초: HTTP $status ← 첫 번째 성공 (리필 시작)"
            else
                ok "  ${sec}초: HTTP $status"
            fi
        else
            warn "  ${sec}초: HTTP $status"
        fi
        sleep 1
    done

    echo
    if [[ -n "$recovered_at" ]]; then
        ok "토큰 리필 시작: 소진 후 약 ${recovered_at}초"
    else
        err "30초 내 리필되지 않음 - rate limit이 매우 엄격하거나 IP 기반 차단일 수 있음"
    fi
    echo
}

# ─────────────────────────────────────────────
# 테스트 5: 분당 총 요청 한도 측정
# 1분 동안 가능한 한 빠르게 요청하여 성공/실패 비율 확인
# ─────────────────────────────────────────────
test_max_throughput() {
    log "=== 테스트 5: 최대 처리량 측정 (60초) ==="
    log "0.1초 간격으로 요청 전송..."
    echo

    local total=0 ok_count=0 rate_limited=0 other=0
    local start_time end_time
    start_time=$(date +%s)
    end_time=$((start_time + 60))

    local first_429=""
    local last_ok_before_429=""

    while [[ $(date +%s) -lt $end_time ]]; do
        local status
        status=$(send_request)
        total=$((total + 1))
        case "$status" in
            200)
                ok_count=$((ok_count + 1))
                last_ok_before_429=$total
                ;;
            429)
                rate_limited=$((rate_limited + 1))
                if [[ -z "$first_429" ]]; then
                    first_429=$total
                fi
                ;;
            *)
                other=$((other + 1))
                ;;
        esac
        sleep 0.1
    done

    local elapsed=$(($(date +%s) - start_time))
    local rate_pct=0
    if [[ $total -gt 0 ]]; then
        rate_pct=$(echo "scale=1; $rate_limited * 100 / $total" | bc)
    fi

    echo
    log "━━━ 결과 요약 ━━━"
    log "  총 요청: $total (${elapsed}초)"
    ok "  성공(200): $ok_count"
    warn "  Rate Limited(429): $rate_limited ($rate_pct%)"
    log "  기타 에러: $other"
    if [[ -n "$first_429" ]]; then
        warn "  첫 429 발생: ${first_429}번째 요청"
    fi
    log "  실효 RPS: $(echo "scale=1; $ok_count / $elapsed" | bc)"
    echo
}

# ─────────────────────────────────────────────
# 메인
# ─────────────────────────────────────────────
main() {
    echo
    log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log "  Mock Worker Rate Limit 탐색 스크립트"
    log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log "  URL: $ENDPOINT"
    log "  API Key: ${API_KEY:0:10}..."
    log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo

    test_connectivity
    test_burst
    test_sustained_rps
    test_refill_rate
    test_max_throughput

    log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log "  테스트 완료!"
    log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo
    log "현재 rate-limiter 설정:"
    log "  max-tokens: 50"
    log "  refill-rate: 10/sec"
    log "  refill-interval-ms: 1000"
    echo
    log "위 결과를 바탕으로 application.yaml의 rate-limiter 설정을 조정하세요."
    log "  - mock worker 서버의 한도보다 낮게 설정해야 429를 방지할 수 있습니다."
    log "  - 권장: max-tokens ≤ (서버 버스트 한도 × 0.8)"
    log "  - 권장: refill-rate ≤ (서버 지속 RPS × 0.8)"
}

main "$@"
