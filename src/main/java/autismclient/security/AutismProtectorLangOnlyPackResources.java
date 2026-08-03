package autismclient.security;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

public final class AutismProtectorLangOnlyPackResources implements PackResources {

    private final PackResources delegate;

    public AutismProtectorLangOnlyPackResources(PackResources delegate) {
        this.delegate = delegate;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        return delegate.getRootResource(paths);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (!isLangResource(type, location.getPath())) return null;
        return delegate.getResource(type, location);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (!isLangPath(type, path)) return;
        delegate.listResources(type, namespace, path, output);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) return Set.of();
        return Set.copyOf(delegate.getNamespaces(type));
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
        return delegate.getMetadataSection(type);
    }

    @Override
    public PackLocationInfo location() {
        return delegate.location();
    }

    @Override
    public String packId() {
        return delegate.packId();
    }

    @Override
    public Optional<KnownPack> knownPackInfo() {
        return delegate.knownPackInfo();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private static boolean isLangResource(PackType type, String path) {
        return type == PackType.CLIENT_RESOURCES
            && path.startsWith("lang/")
            && path.endsWith(".json");
    }

    private static boolean isLangPath(PackType type, String path) {
        return type == PackType.CLIENT_RESOURCES
            && (path.equals("lang") || path.startsWith("lang/"));
    }
}
