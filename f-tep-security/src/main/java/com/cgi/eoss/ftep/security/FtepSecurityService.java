package com.cgi.eoss.ftep.security;

import com.cgi.eoss.ftep.model.FtepEntityWithOwner;
import com.cgi.eoss.ftep.model.FtepService;
import com.cgi.eoss.ftep.model.Group;
import com.cgi.eoss.ftep.model.Role;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.persistence.service.PublishingRequestDataService;
import com.cgi.eoss.ftep.persistence.service.SubscriptionDataService;
import com.cgi.eoss.ftep.persistence.service.UserDataService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.AclPermissionEvaluator;
import org.springframework.security.acls.domain.BasePermission;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * <p>Provides common utility-style methods for interacting with the F-TEP security context.</p>
 */
@Component
@Log4j2
@Transactional
public class FtepSecurityService {
    public static final Authentication PUBLIC_AUTHENTICATION = new PreAuthenticatedAuthenticationToken("PUBLIC", "N/A", ImmutableList.of(FtepPermission.PUBLIC));

    private static final Predicate<GrantedAuthority> ADMIN_PREDICATE = a -> Role.ADMIN.getAuthority().equals(a.getAuthority());
    private static final Predicate<GrantedAuthority> SUPERUSER_PREDICATE = ADMIN_PREDICATE.or(a -> Role.CONTENT_AUTHORITY.getAuthority().equals(a.getAuthority()));

    private final MutableAclService aclService;
    private final AclPermissionEvaluator aclPermissionEvaluator;
    private final UserDataService userDataService;
    private final PublishingRequestDataService publishingRequestDataService;
    private final SubscriptionDataService subscriptionDataService;

    private final Cache<FtepAccessKey, FtepAccess> ftepAccessCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .expireAfterAccess(10, TimeUnit.SECONDS).build();

    @Autowired
    public FtepSecurityService(MutableAclService aclService, AclPermissionEvaluator aclPermissionEvaluator, UserDataService userDataService, PublishingRequestDataService publishingRequestDataService,
            SubscriptionDataService subscriptionDataService) {
        this.aclService = aclService;
        this.aclPermissionEvaluator = aclPermissionEvaluator;
        this.userDataService = userDataService;
        this.publishingRequestDataService = publishingRequestDataService;
        this.subscriptionDataService = subscriptionDataService;
    }

    public void updateOwnerWithCurrentUser(FtepEntityWithOwner entity) {
        entity.setOwner(getCurrentUser());
    }

    public User getCurrentUser() {
        String username = getCurrentAuthentication().getName();
        return userDataService.getByName(username);
    }

    public Collection<? extends GrantedAuthority> getCurrentAuthorities() {
        return getCurrentAuthentication().getAuthorities();
    }

    public Set<Group> getCurrentGroups() {
        return getCurrentUser().getGroups();
    }

    public User refreshPersistentUser(User user) {
        return userDataService.refresh(user);
    }

    /**
     * <p>Create an access control entry granting all users (the PUBLIC authority) READ access to the given object.</p>
     *
     * @param objectClass The class of the entity being published.
     * @param identifier  The identifier of the entity being published.
     */
    @Transactional
    public void publish(Class<?> objectClass, Long identifier) {
        LOG.info("Publishing entity: {} (id: {})", objectClass, identifier);

        publish(new ObjectIdentityImpl(objectClass, identifier));
    }

    /**
     * <p>Create an access control entry granting all users (the PUBLIC authority) READ access to the given object.</p>
     *
     * @param objectIdentity The identifier of the entity being published.
     */
    @Transactional
    public void publish(ObjectIdentity objectIdentity) {
        MutableAcl acl = getAcl(objectIdentity);
        Sid publicSid = new GrantedAuthoritySid(FtepPermission.PUBLIC);

        for (Permission p : getPublishingPermission(objectIdentity).getAclPermissions()) {
            if (acl.getEntries().stream().noneMatch(ace -> ace.getPermission().equals(p) && ace.getSid().equals(publicSid))) {
                acl.insertAce(acl.getEntries().size(), p, publicSid, true);
            }
        }

        saveAcl(acl);
    }

    @Transactional
    public void unpublish(Class<?> objectClass, Long identifier) {
        ObjectIdentity objectIdentity = new ObjectIdentityImpl(objectClass, identifier);
        if (!isPublic(objectIdentity)) {
            LOG.warn("Attempted to unpublish non-public object: {} {}", objectClass, identifier);
            return;
        }

        LOG.info("Unpublishing entity: {} (id: {})", objectClass, identifier);
        MutableAcl acl = getAcl(objectIdentity);

        Sid publicSid = new GrantedAuthoritySid(FtepPermission.PUBLIC);

        // Find the access control entries corresponding to PUBLIC READ access, and delete them
        int aceCount = acl.getEntries().size();
        IntStream.range(0, aceCount).map(i -> aceCount - i - 1)
                .filter(i -> acl.getEntries().get(i).getSid().equals(publicSid) && getPublishingPermission(objectIdentity).getAclPermissions().contains(acl.getEntries().get(i).getPermission()))
                .forEach(acl::deleteAce);

        saveAcl(acl);
    }

    private FtepPermission getPublishingPermission(ObjectIdentity objectIdentity) {
        return objectIdentity.getType().equals(FtepService.class.getCanonicalName())
                ? FtepPermission.SERVICE_USER // for services, grant PUBLIC READ and LAUNCH permissions
                : FtepPermission.READ;
    }

    /**
     * <p>Return the current access properties of the current user on the given object.</p>
     *
     * @param objectClass The class of the entity being tested.
     * @param identifier  The identifier of the entity being tested.
     * @return Whether the object is published or not, as well as the {@link FtepPermission} corresponding to the
     * current access level.
     */
    @Transactional(readOnly = true)
    public FtepAccess getCurrentAccess(Class<?> objectClass, Long identifier) {
        Authentication authentication = getCurrentAuthentication();
        ObjectIdentity objectIdentity = new ObjectIdentityImpl(objectClass, identifier);

        try {
            return ftepAccessCache.get(FtepAccessKey.of(authentication.getName(), objectIdentity), () -> FtepAccess.builder()
                    .published(isPublic(objectIdentity))
                    .publishRequested(isPublishRequested(objectClass, identifier))
                    .currentLevel(getCurrentPermission(authentication, objectIdentity))
                    .build());
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to determine current access level", e);
        }
    }

    private FtepPermission getCurrentPermission(Authentication authentication, ObjectIdentity objectIdentity) {
        if (isSuperUser()) {
            return FtepPermission.SUPERUSER;
        } else if (hasFtepPermission(authentication, FtepPermission.SERVICE_OPERATOR, objectIdentity)) {
            return FtepPermission.SERVICE_OPERATOR;
        } else if (hasFtepPermission(authentication, FtepPermission.ADMIN, objectIdentity)) {
            return FtepPermission.ADMIN;
        } else if (hasFtepPermission(authentication, FtepPermission.SERVICE_DEVELOPER, objectIdentity)) {
            return FtepPermission.SERVICE_DEVELOPER;
        } else if (hasFtepPermission(authentication, FtepPermission.SERVICE_READONLY_DEVELOPER, objectIdentity)) {
            return FtepPermission.SERVICE_READONLY_DEVELOPER;
        } else if (hasFtepPermission(authentication, FtepPermission.SERVICE_USER, objectIdentity)) {
            return FtepPermission.SERVICE_USER;
        } else if (hasFtepPermission(authentication, FtepPermission.WRITE, objectIdentity)) {
            return FtepPermission.WRITE;
        } else if (hasFtepPermission(authentication, FtepPermission.READ, objectIdentity) || isPublic(objectIdentity)) {
            return FtepPermission.READ;
        } else {
            return null;
        }
    }

    /**
     * <p>Verify that the FtepPermission.READ permission is granted on the given object to the static PUBLIC
     * Authentication entity.</p>
     *
     * @param objectClass The class of the entity being tested.
     * @param identifier  The identifier of the entity being tested.
     * @return True if the object is READable by PUBLIC.
     */
    public boolean isPublic(Class<?> objectClass, Long identifier) {
        return isPublic(new ObjectIdentityImpl(objectClass, identifier));
    }

    /**
     * <p>Verify that the FtepPermission.READ permission is granted on the given object to the static PUBLIC
     * Authentication entity.</p>
     *
     * @param objectIdentity The object identity to be tested for PUBLIC visibility.
     * @return True if the object is READable by PUBLIC.
     */
    public boolean isPublic(ObjectIdentity objectIdentity) {
        return hasFtepPermission(PUBLIC_AUTHENTICATION, getPublishingPermission(objectIdentity), objectIdentity);
    }

    public boolean isReadOnlyPublic(ObjectIdentity objectIdentity) {
        return hasFtepPermission(PUBLIC_AUTHENTICATION, FtepPermission.READ, objectIdentity);
    }

    /**
     * <p>Verify that the given user has the given permission on the given object.</p>
     *
     * @param user        The User identity being tested.
     * @param permission  The permission being tested.
     * @param objectClass The class of the entity being tested.
     * @param identifier  The identifier of the entity being tested.
     * @return True if the object has the requested permission for the requested user.
     */
    public boolean hasUserPermission(User user, FtepPermission permission, Class<?> objectClass, Long identifier) {
        return hasUserPermission(user, permission, new ObjectIdentityImpl(objectClass, identifier));
    }

    /**
     * <p>Verify that the given user has the given permission on the given object.</p>
     *
     * @param user           The User identity being tested.
     * @param permission     The permission being tested.
     * @param objectIdentity The object identity to be tested for PUBLIC visibility.
     * @return True if the object has the requested permission for the requested user.
     */
    public boolean hasUserPermission(User user, FtepPermission permission, ObjectIdentity objectIdentity) {
        Authentication authentication = getAuthentication(user);
        return isSuperUser(authentication) || hasFtepPermission(authentication, permission, objectIdentity);
    }

    private Authentication getAuthentication(User user) {
        SecurityUser securityUser = new SecurityUser(userDataService.refresh(user));
        return new PreAuthenticatedAuthenticationToken(securityUser.getUsername(), securityUser.getPassword(), securityUser.getAuthorities());
    }

    @Transactional
    public MutableAcl getAcl(ObjectIdentity objectIdentity, Sid... sids) {
        try {
            return (MutableAcl) aclService.readAclById(objectIdentity, Arrays.asList(sids));
        } catch (NotFoundException nfe) {
            return aclService.createAcl(objectIdentity);
        }
    }

    @Transactional
    public void saveAcl(MutableAcl acl) {
        aclService.updateAcl(acl);
    }

    private List<Sid> getSids(Authentication authentication) {
        return ImmutableList.<Sid>builder()
                .add(new PrincipalSid(authentication))
                .addAll(authentication.getAuthorities().stream().map(GrantedAuthoritySid::new).collect(toSet()))
                .build();
    }

    private boolean hasFtepPermission(Authentication authentication, FtepPermission permission, ObjectIdentity objectIdentity) {
        return permission.getAclPermissions().stream()
                .allMatch(p -> aclPermissionEvaluator.hasPermission(authentication, objectIdentity.getIdentifier(), objectIdentity.getType(), p));
    }

    private Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Transactional(readOnly = true)
    public Set<Long> getVisibleObjectIds(Class<?> objectClass, List<Long> allIds) {
        if (allIds.isEmpty()) {
            return ImmutableSet.of();
        }

        List<Sid> sids = getSids(getCurrentAuthentication());
        List<ObjectIdentity> objectIdentities = allIds.stream().map(id -> new ObjectIdentityImpl(objectClass, id)).collect(toList());

        Map<ObjectIdentity, Acl> objectIdentityAclMap;
        try {
            objectIdentityAclMap = aclService.readAclsById(objectIdentities, sids);
        } catch (NotFoundException e) {
            // Manually build up the ACLs one by one with the null-safe getter
            objectIdentityAclMap = objectIdentities.stream()
                    .collect(toMap(Function.identity(), oid -> this.getAcl(oid, sids.toArray(new Sid[sids.size()]))));
        }

        return objectIdentityAclMap.entrySet().stream()
                .filter(e -> {
                    try {
                        return e.getValue().isGranted(ImmutableList.of(BasePermission.READ), sids, false);
                    } catch (NotFoundException ex) {
                        return false;
                    }
                })
                .map(e -> e.getKey().getIdentifier())
                .map(Long.class::cast)
                .collect(toSet());
    }

    public boolean isSuperUser() {
        return isSuperUser(getCurrentAuthentication());
    }

    public boolean isAdmin() {
        return getCurrentAuthorities().stream().anyMatch(ADMIN_PREDICATE);
    }

    private boolean isPublishRequested(Class<?> objectClass, Long identifier) {
        return !publishingRequestDataService.findOpenByAssociated(objectClass, identifier).isEmpty();
    }

    private boolean isSuperUser(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(SUPERUSER_PREDICATE);
    }

    public boolean isReadableByCurrentUser(Class<?> objectClass, Long objectId) {
        return isSuperUser() || hasFtepPermission(getCurrentAuthentication(), FtepPermission.READ, new ObjectIdentityImpl(objectClass, objectId));
    }

    @Transactional
    public void setParentAcl(Class<?> parentObjectClass, Long parentObjectId, Class<?> childObjectClass, Set<Long> childObjectIds) {
        MutableAcl parentAcl = getAcl(new ObjectIdentityImpl(parentObjectClass, parentObjectId));
        childObjectIds.stream()
                .map(id -> getAcl(new ObjectIdentityImpl(childObjectClass, id)))
                .forEach(acl -> {
                    acl.setParent(parentAcl);
                    saveAcl(acl);
                });
    }

    public boolean isSubscribed() {
        User currentUser = getCurrentUser();
        try {
            LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);
            return currentUser.getRole().equals(Role.ADMIN) || 
                    subscriptionDataService.findByOwner(currentUser).stream().anyMatch(subscription -> subscription.isActive(currentTime));
        } catch (Exception e) {
            return false;
        }
    }

    // Set permissions for a single user
    public void setUserPermission(ObjectIdentity objectIdentity, User owner, FtepPermission permission) {
        LOG.debug("Creating ACL on object {}: {}", objectIdentity, permission);
        MutableAcl acl = getAcl(objectIdentity);
        Sid sid = new PrincipalSid(owner.getName());

        for (Permission p : permission.getAclPermissions()) {
            if (acl.getEntries().stream().noneMatch(ace -> ace.getPermission().equals(p) && ace.getSid().equals(sid))) {
                acl.insertAce(acl.getEntries().size(), p, sid, true);
            }
        }

        saveAcl(acl);
    }

    // Set permissions for a group
    public void setGroupPermission(ObjectIdentity objectIdentity, Group group, FtepPermission permission) {
        LOG.debug("Granting group {} {} permissions for object {}", group.getName(), permission, objectIdentity);
        MutableAcl acl = getAcl(objectIdentity);
        Sid sid = new GrantedAuthoritySid(group);

        for (Permission p : permission.getAclPermissions()) {
            if (acl.getEntries().stream().noneMatch(ace -> ace.getPermission().equals(p) && ace.getSid().equals(sid))) {
                acl.insertAce(acl.getEntries().size(), p, sid, true);
            }
        }

        saveAcl(acl);
    }

    public boolean hasGroupPermission(ObjectIdentity objectIdentity, Sid groupSid, FtepPermission permission) {
        MutableAcl acl = getAcl(objectIdentity);
        Set<Permission> existingPermissions = acl.getEntries().stream().filter(ace -> ace.getSid().equals(groupSid)).map(AccessControlEntry::getPermission).collect(Collectors.toSet());
        Set<Permission> requiredPermissions = permission.getAclPermissions();
        return existingPermissions.containsAll(requiredPermissions);

    }

    @Value(staticConstructor = "of")
    private static class FtepAccessKey {
        String sid;
        ObjectIdentity objectIdentity;
    }

}
