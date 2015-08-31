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

public class ResourceMapper {

    private static final String RESOURCES = "/Users/martinpaulo/Documents/Courses/Java8/ResourceMapper/src/ResourceTypes.txt";
    private static final String DIR_TEMPLATES = "/Users/martinpaulo/PycharmProjects/heat-templates";
    private static final String FUNCTIONS = "/Users/martinpaulo/Documents/Courses/Java8/ResourceMapper/src/Functions.txt";

    private static final String[] UNSUPPORTED = {
            "Barbican", "Gnocchi", "Keystone", "Magnum", "Manila", "Mistral", "Neutron", "Sahara", "Zaqar",
            "OS::Cinder::EncryptedVolumeType", "OS::Cinder::VolumeType", "Designate", "OS::Nova::FloatingIP",
            "AWS::EC2::EIP", "AWS::EC2::InternetGateway", "AWS::EC2::NetworkInterface", "AWS::EC2::RouteTable",
            "AWS::EC2::Subnet", "AWS::EC2::SubnetRouteTableAssociation", "AWS::EC2::VPC", "AWS::EC2::VPCGatewayAttachment",
            "OS::Heat::None", "OS::Heat::StructuredDeploymentGroup", "OS::Heat::SoftwareDeploymentGroup",
            "OS::Nova::Flavor", "OS::Heat::Stack"};
    private static final String NATIVE_RESOURCE_URL = "http://docs.openstack.org/developer/heat/template_guide/openstack.html#";
    private static final String AMAZON_RESOURCE_URL = "http://docs.openstack.org/developer/heat/template_guide/cfn.html#";
    private static final String FUNCTION_RESOURCE_URL = "http://docs.openstack.org/developer/heat/template_guide/hot_spec.html#";
    private static final String NOT_YET_SUPPORTED = "\t\tis not yet supported on the NeCTAR cloud.";

    private PrintWriter out;

    public static void main(String[] args) throws IOException {
        new ResourceMapper().run();
    }

    private void run() {
        // should write to file one of these days
        out = new PrintWriter(System.out);
        out.println("## Resources");
        out.println();
        findInTemplates(RESOURCES);
        out.println("## Functions");
        out.println();
        findInTemplates(FUNCTIONS);
        out.flush();
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
        Optional<String> unsupported = Arrays.stream(UNSUPPORTED).
                filter(s -> resourceType.toLowerCase().contains(s.toLowerCase())).
                findFirst();
        out.print(unsupported.isPresent() ? printRedCross() : printGreenTick());
        out.print("[" + resourceType + "](" + baseUrl + resourceType + ")");
        unsupported.ifPresent(x -> out.print(NOT_YET_SUPPORTED));
        out.println("<br />");
    }

    private String getBaseUrl(String resourceType) {
        String baseUrl = FUNCTION_RESOURCE_URL;
        if (resourceType.startsWith("OS::")) {
            baseUrl = NATIVE_RESOURCE_URL;
        } else if (resourceType.startsWith("AWS::")) {
            baseUrl = AMAZON_RESOURCE_URL;
        }
        return baseUrl;
    }

    private String printRedCross() {
        return "<span style=\"color:red\">✘</span>&nbsp;";
    }

    private String printGreenTick() {
        return "<span style=\"color:green\">✔</span>&nbsp;";
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
