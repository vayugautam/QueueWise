package com.queuewise.security;

import com.queuewise.user.domain.User;
import com.queuewise.user.domain.UserStatus;
import com.queuewise.user.persistence.UserRepository;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class QueueWiseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public QueueWiseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String compositeLogin) throws UsernameNotFoundException {
        String[] parts = compositeLogin.split(":", 2);
        if (parts.length != 2) {
            throw new UsernameNotFoundException("Invalid login");
        }

        UUID organizationId;
        try {
            organizationId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException("Invalid login", exception);
        }

        String login = parts[1];
        User user = login.contains("@")
                ? userRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, login)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid login"))
                : userRepository.findByOrganizationIdAndPhone(organizationId, login)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid login"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UsernameNotFoundException("Invalid login");
        }
        return QueueWiseUserPrincipal.from(user);
    }
}
