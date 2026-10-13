
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.Arrays;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

public class RunTests {

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(Arrays.stream(args).map(name -> selectClass(name)).toList())
            .build();

        PrettyTestListener listener = new PrettyTestListener();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        System.exit(listener.allPassed() ? 0 : 1);
    }
}
