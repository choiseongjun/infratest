# DevOps Practice: Spring Boot + Kubernetes + Argo CD

이 프로젝트는 쿠버네티스(Kubernetes) 환경에서 애플리케이션을 배포하고, **Argo CD**를 이용해 GitOps 파이프라인을 구축 및 실습해볼 수 있는 예제 프로젝트입니다.

---

## 🚀 실습 흐름 요약

1. **로컬 테스트**: 멀티 스테이지 빌드로 Docker 이미지 빌드 후 로컬 실행 및 확인
2. **쿠버네티스 수동 배포**: `kubectl`을 통해 리소스를 수동으로 올려 환경 검증
3. **Argo CD 설치 및 배포**: Argo CD를 설치하고 Git 저장소를 바라보게 설정
4. **GitOps 실습**: 매니페스트 코드를 수정해 GitHub에 푸시 후, 자동 동기화(Auto-Sync)되는 모습 관찰

---

## 1. Docker 이미지 빌드 및 푸시

로컬 환경에 Java/Gradle이 없어도 Docker만 있으면 아래 명령어로 빌드할 수 있습니다.

### Docker 이미지 빌드
```bash
# 본인의 GitHub 계정명 또는 Docker Hub 계정명에 맞게 태그 지정
docker build -t choiseongjun/infratest:latest .
```

### 로컬 Docker 컨테이너에서 임시 테스트
```bash
docker run -d -p 8080:8080 --name test-api \
  -e APP_ENV=local-docker \
  -e APP_MESSAGE="로컬 도커에서 수동으로 띄운 API입니다." \
  choiseongjun/infratest:latest
```
* 웹 브라우저나 curl을 통해 `http://localhost:8080/` 접속 시 상태 JSON이 출력되는지 확인합니다.
* 테스트 후 컨테이너 삭제: `docker rm -f test-api`

### Docker Hub로 이미지 푸시
Argo CD 및 쿠버네티스가 이미지를 내려받을 수 있도록 Docker Hub에 푸시합니다.
```bash
# Docker Hub 로그인 (최초 1회)
docker login

# 이미지 푸시
docker push choiseongjun/infratest:latest
```

---

## 2. 쿠버네티스에 수동 배포 및 테스트 (선택 사항)

Argo CD를 쓰기 전에 쿠버네티스 매니페스트가 잘 동작하는지 `kubectl`로 먼저 테스트해 봅니다.

### 매니페스트 수동 적용
```bash
# k8s 폴더 내의 모든 리소스(ConfigMap, Deployment, Service, Ingress) 적용
kubectl apply -f k8s/
```

### 배포 상태 확인
```bash
# Pod 및 Service 확인
kubectl get all
```

### 포트 포워딩을 통한 접속 테스트
외부 Ingress Controller 설정이 번거롭다면 포트 포워딩을 통해 직접 접속할 수 있습니다.
```bash
kubectl port-forward svc/devops-api-service 8080:80
```
이제 브라우저에서 `http://localhost:8080/`에 접속하면, 3개의 Pod 중 하나가 응답하며 아래와 같은 JSON을 반환합니다:
```json
{
  "hostname": "devops-api-deployment-xxxxx-xxxxx",
  "environment": "kubernetes-production",
  "message": "안녕하세요! 쿠버네티스 Pod에서 실행중인 Spring Boot API입니다!",
  "timestamp": "2026-07-06T07:12:34.567"
}
```
* **동작 검증 완료 후 리소스 초기화**: `kubectl delete -f k8s/`

---

## 3. Argo CD 설치 및 애플리케이션 등록

이제 GitOps 도구인 Argo CD를 통해 배포 프로세스를 자동화합니다.

### 3.1 Argo CD 설치
쿠버네티스 클러스터에 Argo CD 네임스페이스를 만들고 설치 매니페스트를 적용합니다.
```bash
# 네임스페이스 생성
kubectl create namespace argocd

# Argo CD 설치 리소스 다운로드 및 적용
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

### 3.2 Argo CD 서버 접속 및 로그인

#### 포트 포워딩 실행
```bash
kubectl port-forward svc/argocd-server -n argocd 8443:443
```
이제 웹 브라우저에서 `https://localhost:8443` 으로 접속합니다. (보안 경고 화면이 나타나면 '고급 -> 무시하고 계속 진행'을 누르세요.)

#### 초기 비밀번호 조회
* **아이디**: `admin`
* **비밀번호**: 아래 명령어를 수행하여 얻은 텍스트를 입력합니다.

* **Linux / macOS**:
  ```bash
  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
  ```
* **Windows (PowerShell)**:
  ```powershell
  [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String((kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}")))
  ```

---

## 4. GitOps 배포 파이프라인 작동하기

프로젝트에 포함된 `argocd/application.yaml`을 사용하여 Argo CD 애플리케이션을 선언적으로 등록합니다.

### 4.1 소스 코드 GitHub 푸시
현재 로컬 코드를 생성하신 Git 저장소(`https://github.com/choiseongjun/infratest`)에 푸시합니다.
```bash
git init
git remote add origin https://github.com/choiseongjun/infratest.git
git branch -M main
git add .
git commit -m "Initial commit: Spring Boot (Gradle) and Kubernetes manifests"
git push -u origin main
```

### 4.2 Argo CD Application 리소스 적용
```bash
kubectl apply -f argocd/application.yaml
```
적용 후 `https://localhost:8443` 대시보드에 들어가면 **`devops-practice-app`**이라는 카드가 생성되고 자동으로 동기화(Syncing)가 진행되며, 잠시 후 모든 리소스가 초록색(`Healthy` / `Synced`)으로 활성화됩니다!

---

## 5. 실습: GitOps 동기화 체험하기 (핵심!)

1. 로컬 코드 내의 [configmap.yaml](file:///c:/ex/infratest/k8s/configmap.yaml) 파일로 이동합니다.
2. `APP_MESSAGE` 값을 다른 메시지(예: `"Argo CD가 자동으로 동기화해준 메시지입니다!"`)로 변경합니다.
3. 수정한 내용을 커밋 후 GitHub에 푸시합니다:
   ```bash
   git add k8s/configmap.yaml
   git commit -m "Update ConfigMap message for GitOps test"
   git push origin main
   ```
4. Argo CD 대시보드를 지켜봅니다 (또는 수동으로 `Refresh` 버튼 클릭).
5. Argo CD가 저장소의 변경사항을 감지하여 K8s 클러스터 내의 ConfigMap을 실시간으로 업데이트하고, 이 정보를 기반으로 기존 Pod들이 롤링 업데이트되는 모습을 확인할 수 있습니다!
6. `kubectl port-forward svc/devops-api-service 8080:80`을 다시 실행하고 `http://localhost:8080/`에 접속해 변경된 메시지를 최종 확인합니다.
