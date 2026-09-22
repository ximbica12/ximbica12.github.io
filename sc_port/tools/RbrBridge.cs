using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Web.Script.Serialization;

class RbrBridge {
  static string DataDir;
  static HashSet<object> Seen=new HashSet<object>(new RefEq());
  const int MaxDepth=18, MaxList=200000;
  [DllImport("kernel32",SetLastError=true,CharSet=CharSet.Unicode)] static extern bool SetDllDirectory(string lpPathName);

  class RefEq:IEqualityComparer<object>{
    public new bool Equals(object a,object b){return Object.ReferenceEquals(a,b);}
    public int GetHashCode(object o){return System.Runtime.CompilerServices.RuntimeHelpers.GetHashCode(o);}
  }

  static int Main(string[] args){
    if(args.Length<3){Console.Error.WriteLine("usage: RbrBridge <game_data_dir> <input.rbr> <output.json>");return 2;}
    DataDir=Path.GetFullPath(args[0]); string rbr=Path.GetFullPath(args[1]), output=Path.GetFullPath(args[2]);
    if(!Environment.Is64BitProcess){Console.Error.WriteLine("x64 required");return 10;}
    AppDomain.CurrentDomain.AssemblyResolve += Resolve;
    SetDllDirectory(DataDir);
    Environment.SetEnvironmentVariable("PATH",DataDir+";"+(Environment.GetEnvironmentVariable("PATH")??""));
    try{
      Assembly sharp=Assembly.LoadFrom(Path.Combine(DataDir,"SharpKmyCore.dll"));
      Assembly common=Assembly.LoadFrom(Path.Combine(DataDir,"common.dll"));
      Type ct=common.GetType("Yukar.Common.Catalog",true);
      Type ft=ct.GetNestedType("FileType",BindingFlags.Public|BindingFlags.NonPublic);
      Type ow=ct.GetNestedType("OVERWRITE_RULES",BindingFlags.Public|BindingFlags.NonPublic);
      SetStatic(ct,"sEngineMode",true); SetStatic(ct,"sIgnoreMissingMode",true); SetStatic(ct,"sResourceDir",DataDir+Path.DirectorySeparatorChar);
      object catalog=Activator.CreateInstance(ct,new object[]{false});
      Type ut=common.GetType("Yukar.Common.Util",true);
      FieldInfo cf=ut.GetField("sCurrentCatalog",BindingFlags.Public|BindingFlags.NonPublic|BindingFlags.Static);
      if(cf!=null) cf.SetValue(null,catalog);
      object ftrbr=Enum.Parse(ft,"RBR"), always=Enum.Parse(ow,"ALWAYS");
      MethodInfo load=ct.GetMethod("load",new Type[]{ft,typeof(Stream),ow,typeof(bool)});
      int result;
      using(FileStream fs=File.OpenRead(rbr)) result=Convert.ToInt32(load.Invoke(catalog,new object[]{ftrbr,fs,always,true}));
      object items=ct.GetMethod("getFullList",Type.EmptyTypes).Invoke(catalog,null);
      var root=new Dictionary<string,object>();
      root["source"]=rbr; root["catalog_load_result"]=result; root["items"]=Norm(items,0);
      Directory.CreateDirectory(Path.GetDirectoryName(output));
      var js=new JavaScriptSerializer();js.MaxJsonLength=Int32.MaxValue;js.RecursionLimit=100;
      File.WriteAllText(output,js.Serialize(root),new UTF8Encoding(false));
      Console.WriteLine("OK "+output);return 0;
    }catch(Exception ex){Console.Error.WriteLine(ex.ToString());return 1;}
  }
  static Assembly Resolve(object s,ResolveEventArgs e){
    try{string n=new AssemblyName(e.Name).Name;string p=Path.Combine(DataDir,n+".dll");if(File.Exists(p))return Assembly.LoadFrom(p);}catch{}
    return null;
  }
  static void SetStatic(Type t,string n,object v){var f=t.GetField(n,BindingFlags.Public|BindingFlags.NonPublic|BindingFlags.Static);if(f!=null&&!f.IsInitOnly)f.SetValue(null,v);}
  static object Norm(object v,int d){
    if(v==null)return null;Type t=v.GetType();
    if(d>MaxDepth)return "<depth:"+t.FullName+">";
    if(t.IsPrimitive||v is decimal||v is string)return v;
    if(v is Guid)return v.ToString(); if(t.IsEnum)return v.ToString();
    if(v is DateTime)return ((DateTime)v).ToString("o");
    if(v is Type)return ((Type)v).FullName;
    if(v is Delegate||v is Stream||v is MemberInfo)return "<skipped:"+t.FullName+">";
    bool track=!t.IsValueType;if(track){if(Seen.Contains(v))return "<cycle:"+t.FullName+">";Seen.Add(v);}
    try{
      IDictionary di=v as IDictionary;if(di!=null){var o=new Dictionary<string,object>();int n=0;foreach(DictionaryEntry kv in di){if(n++>=MaxList)break;o[Convert.ToString(kv.Key)]=Norm(kv.Value,d+1);}return o;}
      if(!(v is string)&&v is IEnumerable){var l=new List<object>();int n=0;foreach(object x in (IEnumerable)v){if(n++>=MaxList)break;l.Add(Norm(x,d+1));}return l;}
      var obj=new Dictionary<string,object>();obj["$type"]=t.FullName;
      foreach(var f in t.GetFields(BindingFlags.Public|BindingFlags.Instance)){try{obj[f.Name]=Norm(f.GetValue(v),d+1);}catch{}}
      foreach(var p in t.GetProperties(BindingFlags.Public|BindingFlags.Instance)){if(!p.CanRead||p.GetIndexParameters().Length!=0||obj.ContainsKey(p.Name))continue;try{obj[p.Name]=Norm(p.GetValue(v,null),d+1);}catch{}}
      return obj;
    }finally{if(track)Seen.Remove(v);}
  }
}
