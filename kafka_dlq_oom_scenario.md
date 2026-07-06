# Kafka DLQ & OOM 장애 대응 실습 가이드 (대시보드 통합 버전)

이 프로젝트는 제한된 컨테이너 메모리 환경에서 Kafka의 **Dead Letter Queue (DLQ)** 작동 과정과 **OOM(Out Of Memory)** 장애 상황을 전용 웹 대시보드를 통해 실시간으로 관찰하고 학습하는 실습 환경입니다.

---

## 🖥️ 실시간 관제 대시보드 접속 방법

실습용 웹 대시보드는 Spring Boot의 내장 정적 웹 리소스 기능을 통해 제공됩니다. 

1. **포트포워딩 실행**:
   쿠버네티스에 배포된 애플리케이션 서비스 포트를 로컬로 포워딩합니다.
   ```bash
   kubectl port-forward svc/devops-api-service 8080:80
   ```
2. **웹 브라우저 접속**:
   아래 URL을 인터넷 브라우저에 입력해 관제 화면을 실행합니다.
   * **주소**: [http://localhost:8080/](http://localhost:8080/)
   * **화면 제공**: 실시간 JVM Heap 메모리 점유율 게이지, Dead Letter Queue 격리 테이블, 이벤트 발생 이력 실시간 콘솔, 시나리오 컨트롤러

---

## 🛠️ 실습 시나리오 진행 절차

### 1단계: 빌드 및 쿠버네티스 배포

코드가 대폭 추가/수정되었으므로 Docker 이미지를 다시 빌드하고 K8s 클러스터에 반영합니다.

```bash
# 1. gradle 빌드
./gradlew bootJar

# 2. Docker 이미지 빌드 및 푸시 (본인의 레포지토리 정보에 맞춰 태그 지정)
docker build -t choiseongjun/infratest:latest .
docker push choiseongjun/infratest:latest

# 3. K8s 리소스 반영
kubectl apply -f k8s/
```

---

### 2단계: 웹 대시보드로 장애 시나리오 테스트

[http://localhost:8080/](http://localhost:8080/) 에 접속한 뒤, 각 제어 패널 버튼을 눌러보며 학습을 진행합니다.

#### 1. 정상 비즈니스 로직 처리
- **동작**: **[정상 주문 전송]** 버튼을 클릭합니다.
- **관측**:
  - 이벤트 관제 모니터에 `✅ 정상 주문 등록 완료`가 출력됩니다.
  - 카프카 컨슈머가 메시지를 받아 즉시 처리(Redis 캐시 및 Opensearch 인덱싱 완료)합니다.

#### 2. 예외 복구 및 격리 (Kafka Dead Letter Queue)
- **동작**: **[DLQ 유도 주문 전송 (실패 테스트)]** 버튼을 클릭합니다.
- **관측**:
  - 해당 주문의 상품명은 카프카에서 인위적인 유효성 오류를 발생시키는 `FAIL-DLQ`로 자동 생성됩니다.
  - 백엔드에서는 Spring Kafka의 `@RetryableTopic` 설정에 따라 1초 대기 -> 2초 대기 백오프를 거치며 **총 3회 재시도**를 처리합니다.
  - 최종 3회 실패 후, 메시지는 격리 대상 토픽인 `order-events-dlt`로 이동됩니다.
  - 대시보드의 **"Dead Letter Queue 격리 보관소"** 테이블에 실패 메시지 페이로드 JSON과 상태가 실시간으로 자동 노출됩니다.

#### 3. 트래픽 체증 및 컨슈머 지연 (Kafka Consumer Lag)
- **동작**: 
  - **[컨슈머 처리 지연 (5초 지연)]** 스위치를 **ON(활성화)**으로 켭니다.
  - 그 상태에서 **[정상 주문 전송]** 버튼을 여러 번 눌러 대량의 메시지를 쏩니다.
- **관측**:
  - 카프카 컨슈머가 1건을 처리할 때마다 5초씩 대기하므로, 처리되지 못한 메시지들이 카프카 큐 내에 누적(Consumer Lag 발생)됩니다.
  - 터미널에서 카프카 컨슈머 그룹을 조회하여 Lag 수치를 직접 확인할 수 있습니다.
    ```bash
    kubectl exec -it svc/kafka-service -- /opt/kafka/bin/kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 \
      --describe --group devops-group
    ```

#### 4. 리소스 초과 및 인프라 자동 복구 (Kubernetes OOMKilled)
- **동작**: **[JVM 힙 메모리 채우기 (OOM 유발)]** 버튼을 3~4회 연속 클릭합니다.
- **관측**:
  - 한 번 클릭할 때마다 50MB 크기의 Static Leak 배열이 메모리에 누적됩니다.
  - 대시보드의 **JVM Heap 사용량 게이지**가 실시간으로 치솟는 모습(80% -> 90% 이상)이 애니메이션으로 표현됩니다.
  - 메모리가 K8s 제한 한도(256Mi)에 도달하면, 쿠버네티스 엔진은 해당 Pod가 시스템 오동작을 유발하지 않도록 즉각적으로 `OOMKilled` (Exit Code 137) 처리합니다.
  - 그 순간 대시보드 화면 전체가 흐려지며 **"서버 통신 연결 끊김 (OFFLINE)"** 오버레이 화면으로 전환됩니다.
  - 터미널에서 `kubectl get pods -w` 명령을 쳐두면 해당 Pod가 강제 종료 후 다시 `Running`으로 자가 치유(Self-Healing)되는 것을 관찰할 수 있습니다.
  - Pod가 성공적으로 재부팅되어 정상 상태로 복귀하면 대시보드가 자동으로 다시 **"CONNECTED (ONLINE)"**으로 갱신되며, 메모리가 초기화되어 정상 관제가 이루어집니다.
