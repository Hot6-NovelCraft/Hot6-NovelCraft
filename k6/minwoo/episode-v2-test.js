import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '30s', target: 150 },
        { duration: '30s', target: 200 },
        { duration: '30s', target: 250 },
        { duration: '30s', target: 300 },
        { duration: '30s', target: 400 },
        { duration: '30s', target: 500 },
        { duration: '30s', target: 600 },
        { duration: '30s', target: 700 },
        { duration: '30s', target: 800 },
        { duration: '30s', target: 900 },
        { duration: '30s', target: 1000 },
        { duration: '30s', target: 0 },
    ],
};

export function setup() {
    const res = http.post('http://localhost:8080/api/auth/login',
        JSON.stringify({
            email: 'author1@test.com',
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

    // 1화만 집중적으로 (캐시 효과 극대화)
    const episodeId = 1;

    const res = http.get(`http://localhost:8080/api/v2/episodes/${episodeId}`, { headers });

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    // sleep(0.1);
}