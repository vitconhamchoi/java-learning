# Lesson 20: Kubernetes & Production Deployment

## Giới thiệu

Kubernetes (K8s) là nền tảng orchestration container phổ biến nhất cho production deployments. Tự động hóa deployment, scaling, và management của containerized applications.

## Kubernetes Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Kubernetes Cluster                       │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐     │
│  │                    Control Plane                        │     │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────┐ │     │
│  │  │ API      │  │Scheduler │  │Controller│  │  etcd │ │     │
│  │  │ Server   │  │          │  │ Manager  │  │       │ │     │
│  │  └──────────┘  └──────────┘  └──────────┘  └───────┘ │     │
│  └────────────────────────────────────────────────────────┘     │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │   Worker Node 1  │  │   Worker Node 2  │                    │
│  │  ┌─────────────┐ │  │  ┌─────────────┐ │                    │
│  │  │  Pod 1      │ │  │  │  Pod 3      │ │                    │
│  │  │  ┌─────────┐│ │  │  │  ┌─────────┐│ │                    │
│  │  │  │Container││ │  │  │  │Container││ │                    │
│  │  │  └─────────┘│ │  │  │  └─────────┘│ │                    │
│  │  │  Pod 2      │ │  │  │  Pod 4      │ │                    │
│  │  └─────────────┘ │  │  └─────────────┘ │                    │
│  │  kubelet kube-   │  │  kubelet kube-   │                    │
│  │  proxy           │  │  proxy           │                    │
│  └──────────────────┘  └──────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
```

## Key K8s Objects

### Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1          # Max extra pods during update
      maxUnavailable: 0    # Zero downtime
  template:
    spec:
      containers:
        - name: order-service
          image: order-service:1.0.0
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
```

### Service (Load Balancing)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  selector:
    app: order-service     # Routes to pods with this label
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP          # Internal only
```

### HPA (Horizontal Pod Autoscaler)
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70    # Scale up when CPU > 70%
```

### ConfigMap
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
data:
  ENVIRONMENT: "production"
  LOG_LEVEL: "INFO"
```

### Ingress (External Traffic)
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    nginx.ingress.kubernetes.io/rate-limit: "100"
spec:
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /api/orders
            backend:
              service:
                name: order-service
                port:
                  number: 80
```

## Health Probes

### Liveness vs Readiness vs Startup

```
Liveness Probe:
→ Is the container alive (not deadlocked)?
→ Failure: restart the container
→ Endpoint: /actuator/health/liveness

Readiness Probe:
→ Is the container ready to serve traffic?
→ Failure: remove from load balancer (no restart)
→ Endpoint: /actuator/health/readiness

Startup Probe:
→ Has the application started?
→ Prevents liveness/readiness from running until startup complete
→ For slow-starting containers
```

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30     # Wait 30s before first check
  periodSeconds: 10           # Check every 10s
  failureThreshold: 3         # Restart after 3 failures

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
  failureThreshold: 3         # Remove from LB after 3 failures
```

### Spring Boot Health Probes
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true        # Enable /liveness and /readiness
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

## Graceful Shutdown

```yaml
# K8s sends SIGTERM → app should drain connections
spec:
  terminationGracePeriodSeconds: 60    # K8s waits max 60s
  containers:
    - lifecycle:
        preStop:
          exec:
            command: ["/bin/sh", "-c", "sleep 15"]  # Wait for LB to update
```

```yaml
# Spring Boot graceful shutdown
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # Drain connections for 30s

server:
  shutdown: graceful                  # Enable graceful shutdown
```

```
Timeline:
0s:  K8s sends SIGTERM
0s:  preStop hook starts (sleep 15s)
15s: preStop done, Spring starts draining
45s: All connections drained
60s: Pod terminated (or SIGKILL if timeout)
```

## Rolling Update Strategy

```
Before update:
Pod v1 Pod v1 Pod v1

Rolling update (maxSurge=1, maxUnavailable=0):
Pod v1 Pod v1 Pod v1 Pod v2  ← add new
Pod v1 Pod v1 Pod v2         ← remove old
Pod v1 Pod v2 Pod v2         ← continue
Pod v2 Pod v2 Pod v2         ← done

Zero downtime deployment!
```

## Project Structure

```
lesson-20-kubernetes/
├── order-service/
│   ├── src/main/java/com/distributed/k8s/
│   │   ├── K8sOrderApplication.java
│   │   ├── model/Order.java
│   │   ├── service/OrderService.java
│   │   └── controller/OrderController.java
│   └── src/main/resources/application.yml
├── k8s/
│   ├── configmap.yml
│   ├── deployment.yml
│   ├── service.yml
│   ├── hpa.yml
│   └── ingress.yml
└── docker-compose.yml
```

## Chạy với Docker Compose (Local Dev)

```bash
docker-compose up -d

# Test service
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","quantity":3,"amount":99.99}'

# Instance info (shows which "pod" processed request)
curl http://localhost:8080/api/orders/info

# Health checks
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

## Deploy to Kubernetes

```bash
# 1. Build Docker image
docker build -t order-service:1.0.0 ./order-service

# 2. Push to registry (Docker Hub, ECR, GCR)
docker tag order-service:1.0.0 your-registry/order-service:1.0.0
docker push your-registry/order-service:1.0.0

# 3. Update deployment.yml với image name
sed -i 's|your-registry/order-service:1.0.0|actual-registry/order-service:1.0.0|' k8s/deployment.yml

# 4. Apply manifests
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/deployment.yml
kubectl apply -f k8s/service.yml
kubectl apply -f k8s/hpa.yml
kubectl apply -f k8s/ingress.yml

# 5. Check deployment
kubectl get pods -l app=order-service
kubectl get deployment order-service
kubectl get hpa order-service-hpa

# 6. Test
kubectl port-forward svc/order-service 8080:80
curl http://localhost:8080/api/orders/info

# 7. Scale test
kubectl scale deployment order-service --replicas=5

# 8. Rolling update
kubectl set image deployment/order-service order-service=your-registry/order-service:2.0.0
kubectl rollout status deployment/order-service

# 9. Rollback if needed
kubectl rollout undo deployment/order-service

# 10. Watch HPA in action
kubectl get hpa order-service-hpa --watch
```

## Pod Anti-Affinity (High Availability)

```yaml
# Spread pods across nodes
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchLabels:
              app: order-service
          topologyKey: kubernetes.io/hostname
# Result: each pod on different node
# If one node fails, other pods still running
```

## Production Tips

### 1. Resource Management
```yaml
resources:
  requests:               # Guaranteed resources
    memory: "256Mi"
    cpu: "100m"           # 0.1 CPU core
  limits:                 # Max allowed
    memory: "512Mi"
    cpu: "500m"           # 0.5 CPU core

# OOM Killed: memory limit exceeded → container restart
# CPU throttled: cpu limit exceeded → slowdown (no restart)
```

### 2. Pod Disruption Budget
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: order-service-pdb
spec:
  minAvailable: 2          # Always keep 2 pods running
  selector:
    matchLabels:
      app: order-service
# Prevents: kubectl drain, cluster upgrades from killing all pods
```

### 3. Network Policies
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: order-service-netpol
spec:
  podSelector:
    matchLabels:
      app: order-service
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway    # Only allow from API gateway
```

### 4. Secrets Management
```bash
# Create secret
kubectl create secret generic db-credentials \
  --from-literal=username=admin \
  --from-literal=password=secretpassword

# Use in deployment
env:
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: password
```

## Interview Q&A

**Q: Pod vs Container?**
A: Pod = nhóm containers chạy trên cùng node, share network namespace và volumes. Thường 1 Pod = 1 main container. Sidecar containers (logging, proxy) đi cùng main container trong 1 Pod.

**Q: Service types: ClusterIP, NodePort, LoadBalancer?**
A: ClusterIP: internal only (default). NodePort: expose trên node's port (30000-32767), không dùng production. LoadBalancer: external load balancer (cloud provider). Ingress: L7 routing, cost-effective alternative to LoadBalancer per service.

**Q: Liveness vs Readiness probe?**
A: Liveness: restart container nếu unhealthy (deadlock, OOM). Readiness: remove từ load balancer nếu not ready (startup, maintenance, upstream issues). Readiness dùng nhiều hơn để handle graceful degradation.

**Q: StatefulSet vs Deployment?**
A: Deployment: stateless apps, pods interchangeable. StatefulSet: stateful apps (databases), stable network identity, ordered deployment, persistent volumes per pod. Dùng cho Redis, PostgreSQL, Kafka clusters.

**Q: Làm sao debug Pod bị CrashLoopBackOff?**
A: `kubectl logs pod-name --previous` (logs của crash). `kubectl describe pod pod-name` (events, exit code). `kubectl exec -it pod-name -- /bin/sh` (nếu container đang chạy). Check resource limits, liveness probes, startup time.
