package au.org.v3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

public class ResourceMapper {

    private static final String RESOURCES = "/Users/martinpaulo/Documents/Courses/Java8/ResourceMapper/src/ResourceTypes.txt";
    private static final String DIR_TEMPLATES = "/Users/martinpaulo/PycharmProjects/heat-templates";
    private static final String FUNCTIONS = "/Users/martinpaulo/Documents/Courses/Java8/ResourceMapper/src/Functions.txt";

    private static final String[] UNSUPPORTED = {
            "Barbican", "Gnocchi", "Keystone", "Magnum", "Manila", "Mistral", "Neutron", "Sahara", "Zaqar",
            "OS::Cinder::EncryptedVolumeType", "OS::Cinder::VolumeType", "Designate", "OS::Nova::FloatingIP",
            // "AWS::CloudWatch::Alarm",   // if only because we can't find it in the OpenStack documentation
            "AWS::EC2::EIP", "AWS::EC2::InternetGateway", "AWS::EC2::NetworkInterface", "AWS::EC2::RouteTable",
            "AWS::EC2::Subnet", "AWS::EC2::SubnetRouteTableAssociation", "AWS::EC2::VPC", "AWS::EC2::VPCGatewayAttachment",
            "OS::Heat::None", "OS::Heat::StructuredDeploymentGroup", "OS::Heat::SoftwareDeploymentGroup",
            "OS::Nova::Flavor", "OS::Heat::Stack"};
    private static final String NATIVE_RESOURCE_URL = "http://docs.openstack.org/developer/heat/template_guide/openstack.html#";
    private static final String AMAZON_RESOURCE_URL = "http://docs.openstack.org/developer/heat/template_guide/cfn.html#";
    private static final String FUNCTION_RESOURCE_URL = "http://docs.openstack.org/developer/heat/template_guide/hot_spec.html#";
    private static final String NOT_YET_SUPPORTED = "\t\tis not yet supported on the NeCTAR cloud.";

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
    private static final String GREEN_TICK = "<span style=\"color:green\">✔</span>&nbsp;";
    private static final String RED_CROSS = "<span style=\"color:red\">✘</span>&nbsp;";

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
        out.println("The following is a map showing the list of supported resources, and the templates that showcase them.");
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
            printResourceType(targetWord);
            try {
                // instead of doing a for each, we will do a hacky reduction.
                // this means that we will know if there were values, and if so
                // print an extra line at the end.
                Path path = Files.walk(new File(DIR_TEMPLATES).toPath())
                        .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                        .filter(examineFile(targetWord))
                        .reduce(null, (previous, current) -> {
                            outputPathAsLink(current);
                            return current;
                        });
                if (path != null) {
                    out.println();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private void printResourceType(String resourceType) {
        String baseUrl = getBaseUrl(resourceType);
        Optional<String> unsupported = stream(UNSUPPORTED).
                filter(s -> resourceType.toLowerCase().contains(s.toLowerCase())).
                findFirst();
        out.print(unsupported.isPresent() ? RED_CROSS : GREEN_TICK);
        if (Arrays.asList(CUSTOM_CONSTRAINTS).contains(resourceType) || Arrays.asList(PSEUDO_PARAMETERS).contains(resourceType)) {
            out.print(resourceType);
        } else {
            out.print("[" + resourceType + "](" + baseUrl + resourceType + ")");
        }
        unsupported.ifPresent(x -> out.print(NOT_YET_SUPPORTED));
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

    private void outputPathAsLink(Path path) {
        String relativePath = path.toString().replace("/Users/martinpaulo/PycharmProjects/heat-templates", "");
        Path fileName = path.getFileName();
        out.println("* [" + fileName + "](" + relativePath + ")");
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
