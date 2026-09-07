import http from 'k6/http';
import { check } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 100 },
        { duration: '30s', target: 200 },
        { duration: '30s', target: 300 },
        { duration: '30s', target: 400 },
        { duration: '30s', target: 500 },
        { duration: '30s', target: 0 },
    ],
};

export function setup() {
    const res = http.post('http://localhost:8080/api/auth/login',
        JSON.stringify({
            email: 'author2@test.com',
            password: 'Test1234!'
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const token = res.json('data.accessToken');
    return { token };
}

export default function (data) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': data.token
    };

    // DRAFT 상태 회차 ID
    const episodeId = 20;

    // v1: 동기 호출 (응답까지 11~18초 대기)
    const res = http.post(
        `http://localhost:8080/api/ai/author/v1/episodes/${episodeId}/ai-review`,
        null,
        {
            headers,
            timeout: '60s'
        }
    );

    check(res, {
        'status is 200': (r) => r.status === 200,
        'has comments': (r) => {
            try {
                return r.json('data.comments').length > 0;
            } catch {
                return false;
            }
        },
    });
}