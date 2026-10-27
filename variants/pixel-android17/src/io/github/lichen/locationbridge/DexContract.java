package io.github.lichen.locationbridge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Conservative class-code fingerprint. Excludes APK resources, debug data and code offsets.
 * Constant-pool indices remain significant: harmless pool renumbering may require revalidation.
 * This is a compatibility tripwire, not a proof of all transitive GMS behavior. */
final class DexContract {
    private final byte[] b;
    private final String[] strings,types;
    private final int fields,methods,protos;
    private int cursor;
    private DexContract(byte[] data) throws Exception {
        b=data;
        if(b.length<112||b[0]!='d'||b[1]!='e'||b[2]!='x')throw new IOException("dex magic");
        strings=new String[u(56)];
        for(int i=0;i<strings.length;i++) {
            cursor=u(u(60)+4*i);leb();int start=cursor;
            while(b[cursor]!=0)cursor++;
            strings[i]=new String(b,start,cursor-start,StandardCharsets.UTF_8);
        }
        types=new String[u(64)];
        for(int i=0;i<types.length;i++)types[i]=strings[u(u(68)+4*i)];
        fields=u(84);methods=u(92);protos=u(76);
    }
    private int h(int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    private int u(int p){return h(p)|(h(p+2)<<16);}
    private int leb(){int n=0,s=0,c;do{c=b[cursor++]&255;n|=(c&127)<<s;s+=7;}while((c&128)!=0&&s<35);return n;}
    private int signedLeb(){int n=0,s=0,c;do{c=b[cursor++]&255;n|=(c&127)<<s;s+=7;}while((c&128)!=0&&s<35);return s<32&&(c&64)!=0?n|(-1<<s):n;}
    private String typeList(int p){StringBuilder s=new StringBuilder();if(p!=0)for(int i=0,n=u(p);i<n;i++)s.append(types[h(p+4+2*i)]);return s.toString();}
    private String field(int i){int p=fields+8*i;return types[h(p)]+"."+strings[u(p+4)]+":"+types[h(p+2)];}
    private String method(int i){int p=methods+8*i,q=protos+12*h(p+2);return types[h(p)]+"."+strings[u(p+4)]+"("+typeList(u(q+8))+")"+types[u(q+4)];}
    private String proto(int i){int p=protos+12*i;return "("+typeList(u(p+8))+")"+types[u(p+4)];}
    private void references(DataOutputStream out,int start,int words) throws Exception {
        for(int pos=0;pos<words;) {
            int p=start+2*pos,unit=h(p),op=unit&255,width;
            if(op==0) {
                int kind=unit>>>8;
                if(kind==0)width=1;
                else if(kind==1)width=4+2*h(p+2);
                else if(kind==2)width=2+4*h(p+2);
                else if(kind==3)width=4+(h(p+2)*u(p+4)+1)/2;
                else throw new IOException("unknown payload");
            } else if(op==0x18)width=5;
            else if(op==3||op==6||op==9||op==0x14||op==0x17||op==0x1b||op==0x24||op==0x25||op==0x26||op==0x2a||op==0x2b||op==0x2c||(op>=0x6e&&op<=0x72)||(op>=0x74&&op<=0x78))width=3;
            else if(op==2||op==5||op==8||op==0x13||op==0x15||op==0x16||op==0x19||op==0x1a||op==0x1c||op==0x1f||op==0x20||op==0x22||op==0x23||op==0x29||(op>=0x2d&&op<=0x3d)||(op>=0x44&&op<=0x6d)||(op>=0x90&&op<=0xaf)||(op>=0xd0&&op<=0xe2))width=2;
            else if(op>=0xe3)throw new IOException("unsupported dex opcode");
            else width=1;
            if(pos+width>words)throw new IOException("instruction overrun");
            String ref=null;
            if(op==0x1a)ref=strings[h(p+2)];
            else if(op==0x1b)ref=strings[u(p+2)];
            else if(op==0x1c||op==0x1f||op==0x20||op==0x22||op==0x23||op==0x24||op==0x25)ref=types[h(p+2)];
            else if(op>=0x52&&op<=0x6d)ref=field(h(p+2));
            else if((op>=0x6e&&op<=0x72)||(op>=0x74&&op<=0x78))ref=method(h(p+2));
            if(ref!=null){out.writeInt(pos);out.writeUTF(ref);}
            pos+=width;
        }
        out.writeInt(-1);
    }
    private void value() {
        int tag=b[cursor++]&255,kind=tag&31,size=(tag>>>5)+1;
        if(kind==28){int n=leb();for(int i=0;i<n;i++)value();}
        else if(kind==29){leb();int n=leb();for(int i=0;i<n;i++){leb();value();}}
        else if(kind!=30&&kind!=31)cursor+=size;
    }
    private String fingerprint(int p) throws Exception {
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(buffer);
        out.writeUTF("location-contract-v1");out.writeUTF(types[u(p)]);out.writeInt(u(p+4));
        out.writeUTF(u(p+8)==-1?"":types[u(p+8)]);out.writeUTF(typeList(u(p+12)));
        int data=u(p+24);if(data==0)throw new IOException("missing class data");cursor=data;
        int sf=leb(),inf=leb(),dm=leb(),vm=leb();
        for(int count:new int[]{sf,inf}) {
            out.writeInt(count);int index=0;
            for(int i=0;i<count;i++){index+=leb();int access=leb();out.writeUTF(field(index));out.writeInt(access);}
        }
        for(int count:new int[]{dm,vm}) {
            out.writeInt(count);int index=0;
            for(int i=0;i<count;i++) {
                index+=leb();int access=leb(),code=leb(),saved=cursor;
                out.writeUTF(method(index));out.writeInt(access);out.writeBoolean(code!=0);
                if(code!=0) {
                    out.write(b,code,8); // registers, arguments, outgoing words, try count
                    int words=u(code+12),end=code+16+2*words;out.writeInt(words);out.write(b,code+16,2*words);references(out,code+16,words);
                    int tries=h(code+6);
                    if(tries!=0) {
                        int start=end+(words%2)*2;cursor=start+8*tries;int handlers=leb();
                        for(int j=0;j<handlers;j++) {
                            int size=signedLeb();
                            for(int k=0;k<Math.abs(size);k++){out.writeUTF(types[leb()]);leb();}
                            if(size<=0)leb();
                        }
                        out.write(b,start,cursor-start);
                    }
                }
                cursor=saved;
            }
        }
        int values=u(p+28);out.writeBoolean(values!=0);
        if(values!=0){cursor=values;int n=leb();for(int i=0;i<n;i++)value();out.write(b,values,cursor-values);}
        out.flush();return hex(MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray()));
    }
    static Map<String,String> read(String apk,Collection<String> names) throws Exception {
        Set<String> remaining=new HashSet<>(names);Map<String,String> found=new TreeMap<>();
        try(ZipFile zip=new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()&&!remaining.isEmpty()) {
                ZipEntry entry=entries.nextElement();if(!entry.getName().matches("classes[0-9]*\\.dex"))continue;
                byte[] bytes;try(InputStream in=zip.getInputStream(entry)){bytes=in.readAllBytes();}
                DexContract dex=new DexContract(bytes);
                for(int i=0,n=dex.u(96),base=dex.u(100);i<n;i++) {
                    int p=base+32*i;String descriptor=dex.types[dex.u(p)];
                    String name=descriptor.substring(1,descriptor.length()-1).replace('/','.');
                    if(remaining.remove(name))found.put(name,dex.fingerprint(p));
                }
            }
        }
        if(!remaining.isEmpty())throw new IOException("missing contract classes");
        return found;
    }
    static String hex(byte[] digest){StringBuilder out=new StringBuilder();for(byte v:digest)out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();}
    public static void main(String[] args) throws Exception {
        for(Map.Entry<String,String> e:read(args[0],Arrays.asList(Arrays.copyOfRange(args,1,args.length))).entrySet())System.out.println(e.getKey()+"="+e.getValue());
    }
}
