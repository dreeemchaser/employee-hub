package employeehub.repository;

import employeehub.domain.NotificationPreference;
import employeehub.domain.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

    List<NotificationPreference> findByEmployeeIdOrderByNotificationType(String employeeId);

    Optional<NotificationPreference> findByEmployeeIdAndNotificationType(
            String employeeId, NotificationType notificationType);
}
