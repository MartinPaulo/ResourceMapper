package au.org.v3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.stream;

public class ResourceMapper {

    // should get from heat
    private static final String RESOURCES = "/Users/martinpaulo/Documents/Courses/Java8/ResourceMapper/src/ResourceTypes.txt";
    private static final String DIR_TEMPLATES = "/Users/martinpaulo/PycharmProjects/heat-templates";
    private static final String FUNCTIONS = "/Users/martinpaulo/Documents/Courses/Java8/ResourceMapper/src/Functions.txt";

    private static final String NATIVE_RESOURCE_URL = "http://docs.openstack.org/developer/heat/template_guide/openstack.html#";
    private static final String AMAZON_RESOURCE_URL = "http://docs.openstack.org/developer/heat/template_guide/cfn.html#";
    private static final String FUNCTION_RESOURCE_URL = "http://docs.openstack.org/developer/heat/template_guide/hot_spec.html#";

    private static final String[] CUSTOM_CONSTRAINTS = {
            "nova.flavor", // = heat.engine.resources.server:FlavorConstraint
            "neutron.network", // = heat.engine.clients.os.neutron:NetworkConstraint
            "neutron.port", // = heat.engine.clients.os.neutron:PortConstraint
            "neutron.router", // = heat.engine.clients.os.neutron:RouterConstraint
            "neutron.subnet", // = heat.engine.clients.os.neutron:SubnetConstraint
            "glance.image", // = heat.engine.clients.os.glance:ImageConstraint
            "iso_8601", // = heat.engine.resources.iso_8601:ISO8601Constraint
            "nova.keypair", // = heat.engine.resources.nova_keypair:KeypairConstraint
    };

    private static final String[] PSEUDO_PARAMETERS = {
            "OS::stack_name",
            "OS::stack_id",
            "OS::project_id"
    };
    private static final String RESOURCES_TAG = "## Resources";
    private static final String TEMP_FILE = "/Users/martinpaulo/PycharmProjects/heat-templates/README.tmp";
    private static final String INPUT_FILE = "/Users/martinpaulo/PycharmProjects/heat-templates/README.md";

    private PrintWriter out;

    public static void main(String[] args) throws IOException {
        new ResourceMapper().run();
    }

    private void run() {
        File outFile = new File(TEMP_FILE);
        File inFile = new File(INPUT_FILE);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)))) {
            out = new PrintWriter(new FileOutputStream(outFile));
            try {
                String thisLine;
                while (!RESOURCES_TAG.equals(thisLine = in.readLine())) {
                    out.println(thisLine);
                }
                addContent();
            } finally {
                out.flush();
                out.close();
            }
            in.close();
            if (!inFile.delete()) {
                throw new RuntimeException("Could not delete " + inFile.getAbsolutePath());
            }
            if (!outFile.renameTo(inFile)) {
                throw new RuntimeException("Could not rename " + outFile.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void addContent() {
        out.println(RESOURCES_TAG);
        out.println();
        out.println("The following are the resources covered in the repository: and links to the templates using them.");
        out.println();
        findInTemplates(RESOURCES);
        out.println("## Functions");
        out.println();
        findInTemplates(FUNCTIONS);
        out.println("## Pseudo-Parameters");
        out.println();
        stream(PSEUDO_PARAMETERS).forEach(matchInTemplates());
        out.println("## Custom-constraints");
        out.println();
        stream(CUSTOM_CONSTRAINTS).forEach(matchInTemplates());
        out.println("Map generated by: https://github.com/MartinPaulo/ResourceMapper");
    }

    private void findInTemplates(String targetWordFileName) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(targetWordFileName), StandardCharsets.UTF_8)) {
            List<String> distinctWords = reader.lines().collect(Collectors.toList());
            distinctWords.forEach(matchInTemplates());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Consumer<String> matchInTemplates() {
        return targetWord -> {
            try {
                Stream<Path> pathStream = Files.walk(new File(DIR_TEMPLATES).toPath())
                        .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                        .filter(examineFile(targetWord));
                List<String> templates = pathStream.map(this::outputPathAsLink).collect(Collectors.toList());
                if (templates.size() > 0) {
                    printResourceType(targetWord);
                    out.println();
                    templates.forEach(out::println);
                    out.println();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private void printResourceType(String resourceType) {
        String baseUrl = getBaseUrl(resourceType);
        if (Arrays.asList(CUSTOM_CONSTRAINTS).contains(resourceType) || Arrays.asList(PSEUDO_PARAMETERS).contains(resourceType)) {
            out.print(resourceType);
        } else {
            out.print("[" + resourceType + "](" + baseUrl + resourceType + ")");
        }
        out.println("<br />");
    }

    private String getBaseUrl(String resourceType) {
        if (resourceType.startsWith("OS::")) {
            return NATIVE_RESOURCE_URL;
        } else if (resourceType.startsWith("AWS::")) {
            return AMAZON_RESOURCE_URL;
        }
        return FUNCTION_RESOURCE_URL;
    }

    private String outputPathAsLink(Path path) {
        String relativePath = path.toString().replace("/Users/martinpaulo/PycharmProjects/heat-templates", "");
        Path fileName = path.getFileName();
        return "* [" + fileName + "](" + relativePath + ")";

    }

    private Predicate<? super Path> examineFile(String resourceType) {
        return path -> {
            try {
                return new String(Files.readAllBytes(path)).contains(resourceType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
