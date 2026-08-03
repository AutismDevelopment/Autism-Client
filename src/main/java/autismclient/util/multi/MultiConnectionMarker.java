package autismclient.util.multi;

public interface MultiConnectionMarker {
    boolean autism$isMultiManaged();

    MultiConnectionContext.ProxySpec autism$multiProxy();

    void autism$setMultiManaged(MultiConnectionContext.ProxySpec proxy);

    void autism$clearMultiManaged();
}
