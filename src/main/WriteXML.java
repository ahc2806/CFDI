package main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import mx.sat.cfd33.CMetodoPago;
import mx.sat.cfd33.CMoneda;
import mx.sat.cfd33.CTipoDeComprobante;
import mx.sat.cfd33.CTipoFactor;
import mx.sat.cfd33.CUsoCFDI;
import mx.sat.cfd33.Comprobante;
import mx.sat.cfd33.Comprobante.Conceptos;
import mx.sat.cfd33.Comprobante.Conceptos.Concepto;
import mx.sat.cfd33.Comprobante.Conceptos.Concepto.Impuestos;
import mx.sat.cfd33.Comprobante.Emisor;
import mx.sat.cfd33.Comprobante.Receptor;
import mx.sat.cfd33.ObjectFactory;

import org.apache.xerces.impl.dv.util.Base64;

public class WriteXML {
    
    public void crearComprobante() throws Exception {
        XMLGregorianCalendar fecha = null;
        
        //Genera la fecha de emisión
        try{
            fecha= DatatypeFactory.newInstance().newXMLGregorianCalendar("2020-10-11T14:00:00");
        } catch (DatatypeConfigurationException ex) {
            Logger.getLogger(WriteXML.class.getName()).log(Level.SEVERE,null, ex);
        }
        
        ObjectFactory of = new ObjectFactory();
        Comprobante xml = of.createComprobante();
        
        //Datos del formato
        xml.setVersion("3.3");
        xml.setSerie("S");
        xml.setFolio("123");
        xml.setFecha(fecha);
        xml.setFormaPago("01");
        xml.setCondicionesDePago("En una sóla exhibición");
        //Datos generales
        xml.setSubTotal(new BigDecimal("500.00"));
        //xml.setDescuento(new BigDecimal("0.00"));
        xml.setMoneda(CMoneda.MXN);
        xml.setTipoCambio(new BigDecimal("1"));
        xml.setTotal(new BigDecimal("580.00"));
        xml.setTipoDeComprobante(CTipoDeComprobante.I);
        xml.setMetodoPago(CMetodoPago.PUE);
        xml.setLugarExpedicion("71200");
        
        //Este bloque es para crear el emisor
        xml.setEmisor(createEmisor(of));
        
        //Receptor
        xml.setReceptor(createReceptor(of));
        
        //Conceptos
        xml.setConceptos(createConceptos(of));
        
        //Impuestos totales
        xml.setImpuestos(createImpuestos(of));
        
        //Extraer archivos .cer y .key
        File cer = new File("src/cerkey/cer.cer");
        
        File key = new File("src/cerkey/key.key");
        
        //Agregar certificado y no. de certificado al comprobante, por medio del archivo .cer del contribuyente.
        X509Certificate x509Certificado = getX509Certificate(cer);
        String certificado = getCertificadoBase64(x509Certificado);
        String noCertificado = getNoCertificado(x509Certificado);
        xml.setCertificado(certificado);
        xml.setNoCertificado(noCertificado); 
        
        //Asignar los valores al xml, guardar el comprobante y realizar el sellado digital.
        String cadxml = jaxbObjectToXML(xml);
        
        String cadenaoriginal = "";
        PrivateKey llavePrivada = null;
        String selloDigital = "";
        
        try{
            cadenaoriginal = generarCadenaOriginal(cadxml);
        } catch (TransformerException ex){
            Logger.getLogger(WriteXML.class.getName()).log(Level.SEVERE,null,ex);
        }
        
        //Utilizar el archivo .key del contribuyente, ademas de la contraseña correspondiente
        llavePrivada = getPrivateKey(key,"12345678a");
        
        //Asignar el sello digital como texto
        selloDigital = generarSelloDigital(llavePrivada, cadenaoriginal);
        
        //Agregar el sello digital al xml
        xml.setSello(selloDigital);
        
        String COMPROBANTE_XML = "src/cerkey/cfdi.xml";
        
        JAXBContext context = JAXBContext.newInstance(Comprobante.class);
        Marshaller m = context.createMarshaller();
        
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
        m.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,"http://www.sat.gob.mx/cfd/3 http://www.sat.gob.mx/sitio_internet/cfd/3/cfdv33.xsd");
        m.setProperty("com.sun.xml.bind.namespacePrefixMapper", new DefaultNamespacePrefixMapper());
        m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

        m.marshal(xml,new File(COMPROBANTE_XML));
        System.out.print("XML generado correctamente!");
    }
    
    
    //Metodos
    
    private Emisor createEmisor(ObjectFactory of) {
        Emisor emisor = of.createComprobanteEmisor();
        emisor.setRfc("LAN7008173R5");
        emisor.setNombre("Marcos López Esperanza");
        emisor.setRegimenFiscal("622");
        return emisor;
    }
    
    private Receptor createReceptor(ObjectFactory of) {
        Receptor receptor = of.createComprobanteReceptor();
        receptor.setRfc("SUL010720JN8");
        receptor.setNombre("Publico en General");
        receptor.setUsoCFDI(CUsoCFDI.G_01);
        return receptor;
    }
    
    private Conceptos createConceptos(ObjectFactory of) {
        Conceptos cps = of.createComprobanteConceptos();
        List<Concepto> list = cps.getConcepto();
        Concepto c1 = of.createComprobanteConceptosConcepto();
        c1.setImporte(new BigDecimal("500.00"));
        c1.setValorUnitario(new BigDecimal("500.00"));
        c1.setUnidad("Kilogramo");
        c1.setClaveUnidad("KGM");
        c1.setCantidad(new BigDecimal("1.00"));
        c1.setClaveProdServ("50111515");
        c1.setDescripcion("Pollo en pieza");
        c1.setNoIdentificacion("12345");
        c1.setImpuestos(createImpuestosConcepto(of));
        list.add(c1);
        return cps;
    }
    
    private Impuestos createImpuestosConcepto(ObjectFactory of) {
        Comprobante.Conceptos.Concepto.Impuestos imps = of.createComprobanteConceptosConceptoImpuestos();
        
        //Bloque para los impuestos trasladados.
        Comprobante.Conceptos.Concepto.Impuestos.Traslados trs = of.createComprobanteConceptosConceptoImpuestosTraslados();
        List<Comprobante.Conceptos.Concepto.Impuestos.Traslados.Traslado> list = trs.getTraslado();
        Comprobante.Conceptos.Concepto.Impuestos.Traslados.Traslado t1 = of.createComprobanteConceptosConceptoImpuestosTrasladosTraslado();
        t1.setImporte(new BigDecimal("80.00"));
        t1.setTasaOCuota(new BigDecimal("0.160000"));
        t1.setTipoFactor(CTipoFactor.TASA);
        t1.setImpuesto("002");
        t1.setBase(new BigDecimal("500.00"));
        list.add(t1);
         
        imps.setTraslados(trs);
        return imps;
    }
    
    private Comprobante.Impuestos createImpuestos(ObjectFactory of) {
        Comprobante.Impuestos impus = of.createComprobanteImpuestos();
        
        impus.setTotalImpuestosTrasladados(new BigDecimal("80.00"));
        
        // Bloque para los impuestos transladados
        Comprobante.Impuestos.Traslados tras = of.createComprobanteImpuestosTraslados();
        List<Comprobante.Impuestos.Traslados.Traslado>list = tras.getTraslado();
        Comprobante.Impuestos.Traslados.Traslado t1 = of.createComprobanteImpuestosTrasladosTraslado();
        t1.setImporte(new BigDecimal("80.00"));
        t1.setTasaOCuota(new BigDecimal("0.160000"));
        t1.setTipoFactor(CTipoFactor.TASA);
        t1.setImpuesto("002");
        list.add(t1);
        
        impus.setTraslados(tras);
        return impus;
    }
    
    // Métodos de sellado
    private X509Certificate getX509Certificate(final File certificateFile) throws CertificateException,IOException {
        FileInputStream is = null;
        try {
            is = new FileInputStream(certificateFile);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(is);
        } finally {
            if(is != null) {
                is.close();
            }
        }
    }
    
    private String getCertificadoBase64(final X509Certificate cert) throws CertificateEncodingException{
        return new String (Base64.encode(cert.getEncoded()));
    }
    
    private String getNoCertificado(final X509Certificate cert) {
        BigInteger serial = cert.getSerialNumber();
        byte[] sArr = serial.toByteArray();
        StringBuilder buffer = new StringBuilder ();
        for(int i = 0; i < sArr.length; i++) buffer.append((char) sArr[i]);
        return buffer.toString();
    }
    
    private PrivateKey getPrivateKey(final File keyFile, final String password) throws GeneralSecurityException, IOException {
        FileInputStream in = new FileInputStream(keyFile);
        org.apache.commons.ssl.PKCS8Key pkcs8 = new org.apache.commons.ssl.PKCS8Key(in,password.toCharArray());
        
        byte[] decrypted = pkcs8.getDecryptedBytes();
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decrypted);
        PrivateKey pk = null;
        
        if(pkcs8.isDSA()){
            pk = KeyFactory.getInstance("DSA").generatePrivate(spec);
        } else if(pkcs8.isRSA()){
            pk = KeyFactory.getInstance("RSA").generatePrivate(spec);
        }
        
        pk = pkcs8.getPrivateKey();
        return pk;
    }
    
    private String generarSelloDigital (final PrivateKey key, final String cadenaOriginal)
    throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
        Signature sing = Signature.getInstance("SHA256withRSA");
        sing.initSign(key,new SecureRandom());
        
        try{
            sing.update(cadenaOriginal.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ex){
            Logger.getLogger(WriteXML.class.getName()).log(Level.SEVERE,null,ex);
        }
        
        byte[] signature = sing.sign();
        return new String(Base64.encode(signature));
    }
    
    private String jaxbObjectToXML(Comprobante xml) {
        String xmlString = "";
        
        try{
            JAXBContext context = JAXBContext.newInstance(Comprobante.class);
            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,Boolean.TRUE);
            
            StringWriter sw = new StringWriter();
            m.marshal(xml, sw);
            xmlString = sw.toString();
        } catch (JAXBException e){
            e.printStackTrace();
        }
        
        return xmlString;
    }
    
    private String generarCadenaOriginal(final String xml) throws TransformerException {
        StreamSource streamSource = new StreamSource("src/cerkey/cadenaoriginal_3_3.xslt");
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer xlsTransformer = transformerFactory.newTransformer(streamSource);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        xlsTransformer.transform(new StreamSource(new StringReader(xml)), new StreamResult(output));
        
        String resultado = "";
        
        try{
            resultado = output.toString("UTF-8");
        }catch (UnsupportedEncodingException ex){
            Logger.getLogger(WriteXML.class.getName()).log(Level.SEVERE,null,ex);
        }
        
        return resultado;
    }
}
































