package com.cgi.eoss.ftep.api.controllers;

import com.cgi.eoss.ftep.model.Role;
import com.cgi.eoss.ftep.model.Subscription;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.model.Wallet;
import com.cgi.eoss.ftep.persistence.service.GroupDataService;
import com.cgi.eoss.ftep.persistence.service.SubscriptionDataService;
import com.cgi.eoss.ftep.persistence.service.UserDataService;
import com.cgi.eoss.ftep.security.FtepSecurityService;
import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>A {@link RepositoryRestController} for interacting with {@link User}s. Offers additional functionality over
 * the standard CRUD-style {@link UsersApi}.</p>
 */
@RestController
@BasePathAwareController
@RequestMapping("/users")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class UsersApiExtension {

    private final FtepSecurityService ftepSecurityService;
    private final UserDataService userDataService;
    private final GroupDataService groupDataService;
    private final SubscriptionDataService subscriptionDataService;

    @Value("${ftep.api.trial-group-name:}")
    private String trialGroupName;

    @GetMapping("/current")
    public ResponseEntity currentUser() {
        return ResponseEntity.ok(new Resource<>(ftepSecurityService.getCurrentUser()));
    }

    @GetMapping("/current/groups")
    public ResponseEntity currentGroups() {
        return ResponseEntity.ok(Resources.wrap(ftepSecurityService.getCurrentGroups()));
    }

    @GetMapping("/current/grantedAuthorities")
    public List<String> grantedAuthorities() {
        return ftepSecurityService.getCurrentAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    @PostMapping("/current/startTrial")
    @Transactional
    public ResponseEntity<Void> startTrial() {
        User currentUser = ftepSecurityService.getCurrentUser();
        LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);

        if (!hasAnySubscription(currentUser)) {
            if (currentUser.getRole().equals(Role.GUEST)) {
                currentUser.setRole(Role.USER);
            }
            Subscription subscription = new Subscription(currentUser, currentTime);
            subscriptionDataService.save(subscription);

            if (!Strings.isNullOrEmpty(trialGroupName)) {
                Optional.ofNullable(groupDataService.getByName(trialGroupName)).ifPresent(group -> {
                    Set<User> members = group.getMembers();
                    members.add(currentUser);
                    group.setMembers(members);
                    groupDataService.save(group);
                    currentUser.addGroup(group);
                });
            }
            //currentUser.getWallet().setBalance(1000);
            userDataService.save(currentUser);
            LOG.info(String.format("User %s (%s) has started a free trial", currentUser.getName(), currentUser.getEmail()));

            return ResponseEntity.accepted().build();
        } else {
            LOG.warn(String.format("The free trial for user %s (%s) has already been started", currentUser.getName(), currentUser.getEmail()));
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/current/wallet")
    public ResponseEntity<Resource<Wallet>> getCurrentUserWallet() {
        User currentUser = ftepSecurityService.getCurrentUser();
        return ResponseEntity.ok().body(new Resource<>(currentUser.getWallet()));
    }

    @GetMapping("/current/activeSubscription")
    public ResponseEntity getActiveSubscription() {
        User currentUser = ftepSecurityService.getCurrentUser();
        LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);
        Optional<Subscription> activeSubscription = subscriptionDataService.findByOwner(currentUser).stream()
                .filter(subscription -> subscription.isActive(currentTime)).findFirst();
        if (activeSubscription.isPresent()) {
            return ResponseEntity.ok().body(new Resource<>(activeSubscription.get()));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    private boolean hasActiveSubscription(User user, LocalDateTime currentTime) {
        return subscriptionDataService.findByOwner(user).stream().anyMatch(subscription -> subscription.isActive(currentTime));
    }

    private boolean hasAnySubscription(User user) {
        return subscriptionDataService.findByOwner(user).stream().findAny().isPresent();
    }

}
