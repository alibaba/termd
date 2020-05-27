package io.termd.core.term;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TermInfoDeviceTest {

    @Test
    public void devicesTest() {
        try {
            TermInfo termInfo = TermInfo.defaultInfo();
            Field field = TermInfo.class.getDeclaredField("devices");
            field.setAccessible(true);
            Object devices = field.get(termInfo);

            LinkedHashMap<String, Device> linkedHashMap = (LinkedHashMap<String, Device>)devices;

            System.out.println("linkedHashMap.size()=" + linkedHashMap.size());
            int i = 0;
            for (Map.Entry<String, Device> entry : linkedHashMap.entrySet()) {
                System.out.println("i=" + (i++) + ", key=" + entry.getKey() + ", value=" + entry.getValue());
            }

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public Device getXtermDevice1() {

        Device device = TermInfo.defaultInfo().getDevice("xterm"); // For now use xterm

        return device;
    }

    public Device getXtermDevice2() {

        Device device = null;

        try {
            //InputStream in = TermInfo.class.getResourceAsStream("terminfo-test.src");
            InputStream in = TermInfo.class.getClassLoader().getResourceAsStream("terminfo-test.src");

            TermInfoParser parser = new TermInfoParser(new InputStreamReader(in, "US-ASCII"));
            TermInfoBuilder builder = new TermInfoBuilder();
            parser.parseDatabase(builder);
            TermInfo termInfo = builder.build();

            device = termInfo.getDevice("xterm"); // For now use xterm

        } catch (Throwable t) {
            t.printStackTrace();
        }

        return device;
    }

    @Test
    public void xtermDeviceCompare() {

        Device device1 = getXtermDevice1();
        Device device2 = getXtermDevice2();

        System.out.println("device1.name=" + device1.name);
        System.out.println("device2.name=" + device2.name);
        System.out.println("device1.longname=" + device1.longname);
        System.out.println("device2.longname=" + device2.longname);
        System.out.println("device1.synonyms=" + device1.synonyms);
        System.out.println("device2.synonyms=" + device2.synonyms);

        Assert.assertEquals(device1.name, device2.name);
        Assert.assertEquals(device1.longname, device2.longname);
        Assert.assertEquals(device1.synonyms, device2.synonyms);

        Collection<Feature<?>> features1 = device1.getFeatures();
        List<Feature<?>> list1 = new ArrayList(features1);
        Collection<Feature<?>> features2 = device2.getFeatures();
        List<Feature<?>> list2 = new ArrayList(features2);

        for (int i = 0; i < list1.size(); i++) {
            Feature<?> f1 = list1.get(i);
            Feature<?> f2 = list2.get(i);

            System.out.println(
                "device1 i=" + i
                    + ", f1=" + f1
                    + ", capability=[" + capabilityString(f1.capability()) + "]"
            );

            System.out.println(
                "device1 i=" + i
                    + ", f2=" + f2
                    + ", capability=[" + capabilityString(f2.capability()) + "]"
            );

            Assert.assertEquals(f1, f2);
        }

    }

    private String capabilityString(Capability capability) {

        return "name:" + capability.name + ",variable:+" + capability.variable + ",type:" + capability.type
            + ",description:"
            + capability.description;
    }
}

