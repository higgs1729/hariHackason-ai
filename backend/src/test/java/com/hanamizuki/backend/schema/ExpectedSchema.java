package com.hanamizuki.backend.schema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;

import com.hanamizuki.backend.domain.Album;
import com.hanamizuki.backend.domain.AlbumJob;
import com.hanamizuki.backend.domain.AlbumMember;
import com.hanamizuki.backend.domain.AlbumPhoto;
import com.hanamizuki.backend.domain.AlbumShare;
import com.hanamizuki.backend.domain.Block;
import com.hanamizuki.backend.domain.Capsule;
import com.hanamizuki.backend.domain.CapsuleRecipient;
import com.hanamizuki.backend.domain.Friend;
import com.hanamizuki.backend.domain.FriendQr;
import com.hanamizuki.backend.domain.Notification;
import com.hanamizuki.backend.domain.Photo;
import com.hanamizuki.backend.domain.RefreshToken;
import com.hanamizuki.backend.domain.User;
import com.hanamizuki.backend.domain.converter.FriendStatusConverter;
import com.hanamizuki.backend.domain.converter.MediaTypeConverter;
import com.hanamizuki.backend.domain.converter.MemberRoleConverter;
import com.hanamizuki.backend.domain.converter.UserRoleConverter;

/**
 * Writes the MySQL schema the entities imply, without a database.
 *
 * <p>No connection is opened: the dialect is given explicitly, which is what
 * Hibernate would otherwise have discovered from JDBC metadata.
 */
final class ExpectedSchema {

    /** Listed once, so the export cannot silently miss a table. */
    private static final List<Class<?>> ENTITIES = List.of(
            User.class, RefreshToken.class, Friend.class, FriendQr.class, Block.class,
            Photo.class, Album.class, AlbumMember.class, AlbumPhoto.class,
            AlbumShare.class, Capsule.class, CapsuleRecipient.class,
            AlbumJob.class, Notification.class);

    /**
     * Auto-applied by component scan in the running app, but nothing scans
     * here. Leaving them out makes the three lowercase enums look like
     * ordinals and produces three mismatches that do not exist.
     */
    private static final List<Class<?>> CONVERTERS = List.of(
            UserRoleConverter.class, MemberRoleConverter.class,
            MediaTypeConverter.class, FriendStatusConverter.class);

    private ExpectedSchema() {
    }

    static Path writeTo(Path target) throws IOException {
        Files.deleteIfExists(target);
        Files.createDirectories(target.getParent());

        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect");
        settings.put(AvailableSettings.ALLOW_METADATA_ON_BOOT, "false");
        settings.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        settings.put(AvailableSettings.JAKARTA_HBM2DDL_SCRIPTS_ACTION, "create");
        settings.put(AvailableSettings.JAKARTA_HBM2DDL_SCRIPTS_CREATE_TARGET, target.toString());

        StandardServiceRegistry registry =
                new StandardServiceRegistryBuilder().applySettings(settings).build();

        MetadataSources sources = new MetadataSources(registry);
        ENTITIES.forEach(sources::addAnnotatedClass);
        CONVERTERS.forEach(sources::addAnnotatedClass);

        SchemaManagementToolCoordinator.process(
                sources.buildMetadata(), registry, settings, action -> { });
        return target;
    }
}
