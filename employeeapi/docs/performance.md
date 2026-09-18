# Performance

> ⚠️ **Aspirational / future-looking.** This document describes target performance practices, several of which are **not implemented** in the current codebase: there is no Redis caching, no AWS S3 storage, no Prometheus metrics, and no custom HikariCP tuning (the default Hikari pool ships with `spring-boot-starter-data-jpa`, unconfigured). Benchmark numbers below are illustrative, not measured. Any SQL that references singular table names (e.g. `leave_request`, `timesheet`) should use the actual plural, snake_case Hibernate names (`leave_requests`, `timesheets`). Treat this as a roadmap for performance work, not a description of the running system.

## Performance Overview

The Employee API targets solid performance through database indexing, connection pooling, and caching. This section describes intended practices; see the caveat above for what is and isn't implemented today.

## Current Performance Characteristics

### Baseline Metrics
- **Response Time**: < 100ms for employee retrieval
- **Throughput**: 1000+ requests/second
- **Concurrent Users**: 1000+ simultaneous connections
- **Database Queries**: Optimized with proper indexing
- **Memory Usage**: ~256MB baseline, scales with load

### Architecture Benefits
- Stateless REST API design (JWT-based, no sessions)
- Horizontal scaling capability
- Database connection pooling
- Efficient pagination implementation
- Asynchronous file processing

## Performance Optimization Areas

### 1. Database Optimization

#### Indexing Strategy
```sql
-- Primary key index (automatic)
-- Email uniqueness constraint index (automatic)

-- Additional performance indexes
CREATE INDEX idx_employees_status ON employees(employment_status);
CREATE INDEX idx_employees_department ON employees(department_id);
CREATE INDEX idx_employees_team ON employees(team_id);
CREATE INDEX idx_employees_manager ON employees(manager_id);
CREATE INDEX idx_leave_requests_employee ON leave_request(employee_id);
CREATE INDEX idx_timesheets_employee ON timesheet(employee_id);
```

#### Connection Pool Configuration
```yaml
# application-prod.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 20000
      leak-detection-threshold: 60000
```

### 2. Caching Implementation

#### Redis Caching
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```java
@Service
@CacheConfig
public class EmployeeService {
    @Cacheable(value = "employees", key = "#id")
    public Employee getEmployee(String id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    @CacheEvict(value = "employees", key = "#employee.id")
    public Employee updateEmployee(Employee employee) {
        return employeeRepository.save(employee);
    }
}
```

### 3. File Storage Optimization

#### Cloud Storage Migration
```java
// AWS S3 Implementation
@Service
public class S3PhotoService implements PhotoService {
    private final AmazonS3 s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Override
    public String uploadPhoto(String employeeId, MultipartFile file) {
        String key = "employees/" + employeeId + "/" + generateSecureFilename(file);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        s3Client.putObject(bucketName, key, file.getInputStream(), metadata);

        return s3Client.getUrl(bucketName, key).toString();
    }
}
```

### 4. Application Performance Tuning

#### JVM Optimization
```bash
# Production JVM flags
java -server \
  -Xmx2g -Xms1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseCompressedOops \
  -XX:+OptimizeStringConcat \
  -Djava.security.egd=file:/dev/./urandom \
  -jar employeeapi.jar
```

### 5. Monitoring & Profiling

#### Application Metrics
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

## Performance Benchmarks

### Baseline Performance (Single Instance)
| Operation | Response Time | Throughput |
|-----------|---------------|------------|
| Get Employee | < 50ms | 2000 req/s |
| List Employees | < 100ms | 1500 req/s |
| Create Employee | < 200ms | 800 req/s |
| Upload Photo | < 500ms | 200 req/s |

### Scaling Performance
| Instances | Concurrent Users | Response Time |
|-----------|------------------|---------------|
| 1 | 1000 | < 200ms |
| 3 | 3000 | < 150ms |
| 5 | 5000 | < 100ms |

## Troubleshooting Performance Issues

### Common Bottlenecks
1. **Database Connection Pool Exhausted** — increase pool size, optimize queries, add read replicas
2. **Memory Leaks** — profile JVM heap, check for object retention
3. **Slow File Operations** — move to cloud storage, implement async processing
4. **High CPU Usage** — profile application, optimize algorithms

## Resources

- [Spring Performance Best Practices](https://spring.io/blog/2020/04/30/spring-tips-performance-tuning)
- [JPA Performance Tuning](https://thorben-janssen.com/jpa-performance/)
- [Redis Caching Patterns](https://redis.io/topics/caching)
- [JVM Performance Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/)
