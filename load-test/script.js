import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// k6 부하 시나리오 구성
export const options = {
    stages: [
        { duration: '30s', target: 50 }, // 30초 동안 가상 사용자(VU)를 50명까지 점진적 증가
        { duration: '1m', target: 50 },  // 1분 동안 50명 상태로 부하 유지
        { duration: '10s', target: 0 },  // 10초 동안 가상 사용자 점진적 종료
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95%의 요청은 500ms 이내에 처리되어야 함
        http_req_failed: ['rate<0.01'],   // 에러율 1% 미만 유지
    },
};

// 가상의 상품 및 고객 목록
const products = ['Laptop', 'Smartphone', 'Tablet', 'Smartwatch', 'Headphones', 'Keyboard', 'Monitor'];
const customers = ['Alice', 'Bob', 'Charlie', 'David', 'Eva', 'Frank', 'Grace'];
const statuses = ['CREATED', 'PAID', 'SHIPPED', 'CANCELLED'];

export default function () {
    const host = 'http://localhost:8080';
    
    // 무작위 시나리오 비율 설정 (쓰기 20%, 조회/검색 80%)
    const rand = Math.random();

    if (rand < 0.20) {
        // 1. 주문 생성 API 테스트 (20% 비중)
        const payload = JSON.stringify({
            productName: products[Math.floor(Math.random() * products.length)],
            quantity: randomIntBetween(1, 5),
            price: parseFloat((Math.random() * 1000 + 10).toFixed(2)),
            customerName: customers[Math.floor(Math.random() * customers.length)],
            status: statuses[Math.floor(Math.random() * statuses.length)],
        });

        const params = {
            headers: {
                'Content-Type': 'application/json',
            },
        };

        const res = http.post(`${host}/api/orders`, payload, params);
        check(res, {
            'create order status is 201': (r) => r.status === 201,
            'create order response has ID': (r) => JSON.parse(r.body).id !== undefined,
        });

    } else if (rand < 0.50) {
        // 2. 단건 조회 API 테스트 (30% 비중 - Redis 캐시가 미스나면 DB 조회, 히트되면 캐시 조회)
        // ID 범위는 생성 횟수 감안하여 1 ~ 100 범위 무작위 선택
        const orderId = randomIntBetween(1, 100);
        const res = http.get(`${host}/api/orders/${orderId}`);
        check(res, {
            'get order status is 200 or 404': (r) => r.status === 200 || r.status === 404,
        });

    } else if (rand < 0.80) {
        // 3. 주문 검색 API 테스트 (30% 비중 - Opensearch 호출)
        const keyword = products[Math.floor(Math.random() * products.length)];
        const res = http.get(`${host}/api/orders/search?keyword=${keyword}`);
        check(res, {
            'search status is 200': (r) => r.status === 200,
        });

    } else {
        // 4. 복잡한 통계 집계 API 테스트 (20% 비중 - PostgreSQL 집계 쿼리)
        const res = http.get(`${host}/api/orders/statistics`);
        check(res, {
            'statistics status is 200': (r) => r.status === 200,
            'statistics response contains data': (r) => JSON.parse(r.body).dailySales !== undefined,
        });
    }

    // 가상 사용자별 처리 간격
    sleep(1);
}
