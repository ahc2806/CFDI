package main;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import java.util.HashMap;
import java.util.Map;

public class DefaultNamespacePrefixMapper extends NamespacePrefixMapper{
    
    private Map<String, String> namespaceMap = new HashMap<>();
    
    public DefaultNamespacePrefixMapper(){
        namespaceMap.put("http://www.sat.gob.mx/cfd/3", "cfdi");
        namespaceMap.put("http://www.w3.org/2001/XMLSchema-instance", "xsi");
    }
    
    @Override
    public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix){
        return namespaceMap.getOrDefault(namespaceUri, suggestion);
    }
    
}





