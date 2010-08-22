package org.basex.test.w3c;

import static org.basex.core.Text.*;
import static org.basex.util.Token.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.basex.core.Context;
import org.basex.core.Main;
import org.basex.core.Prop;
import org.basex.core.cmd.Check;
import org.basex.core.cmd.Close;
import org.basex.core.cmd.CreateDB;
import org.basex.data.Data;
import org.basex.data.DataText;
import org.basex.data.Nodes;
import org.basex.data.SerializerProp;
import org.basex.data.XMLSerializer;
import org.basex.io.CachedOutput;
import org.basex.io.IO;
import org.basex.io.TextInput;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.query.expr.Expr;
import org.basex.query.func.FNSimple;
import org.basex.query.func.FunDef;
import org.basex.query.item.DBNode;
import org.basex.query.item.Item;
import org.basex.query.item.Str;
import org.basex.query.item.Type;
import org.basex.query.item.Uri;
import org.basex.query.item.Value;
import org.basex.query.iter.NodIter;
import org.basex.query.iter.ItemIter;
import org.basex.util.Args;
import org.basex.util.Performance;
import org.basex.util.TokenBuilder;
import org.basex.util.TokenList;

/**
 * XQuery Test Suite wrapper.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author Christian Gruen
 */
public abstract class W3CTS {
  // Try "ulimit -n 65536" if Linux tells you "Too many open files."

  /** Inspect flag. */
  private static final byte[] INSPECT = token("Inspect");
  /** Fragment flag. */
  private static final byte[] FRAGMENT = token("Fragment");
  /** XML flag. */
  private static final byte[] XML = token("XML");
  /** Replacement pattern. */
  private static final Pattern SLASH = Pattern.compile("/", Pattern.LITERAL);

  /** Database context. */
  protected final Context context = new Context();
  /** Path to the XQuery Test Suite. */
  protected String path = "";
  /** Data reference. */
  protected Data data;

  /** History path. */
  private final String pathhis;
  /** Log file. */
  private final String pathlog;
  /** Test suite input. */
  private final String input;
  /** Test suite id. */
  private final String testid;

  /** Query path. */
  private String queries;
  /** Expected results. */
  private String expected;
  /** Reported results. */
  private String results;
  /** Reports. */
  private String report;
  /** Test sources. */
  private String sources;

  /** Maximum length of result output. */
  private static int maxout = 500;

  /** Query filter string. */
  private String single;
  /** Flag for printing current time functions into log file. */
  private boolean currTime;
  /** Flag for creating report files. */
  private boolean reporting;
  /** Verbose flag. */
  private boolean verbose;
  /** Debug flag. */
  private boolean debug;
  /** Minimum conformance. */
  private boolean minimum;
  /** Print compilation steps. */
  private boolean compile;

  /** Cached source files. */
  private final HashMap<String, String> srcs = new HashMap<String, String>();
  /** Cached module files. */
  private final HashMap<String, String> mods = new HashMap<String, String>();
  /** Cached collections. */
  private final HashMap<String, byte[][]> colls =
    new HashMap<String, byte[][]>();

  /** OK log. */
  private final StringBuilder logOK = new StringBuilder();
  /** OK log. */
  private final StringBuilder logOK2 = new StringBuilder();
  /** Error log. */
  private final StringBuilder logErr = new StringBuilder();
  /** Error log. */
  private final StringBuilder logErr2 = new StringBuilder();
  /** File log. */
  private final StringBuilder logReport = new StringBuilder();

  /** Error counter. */
  private int err;
  /** Error2 counter. */
  private int err2;
  /** OK counter. */
  private int ok;
  /** OK2 counter. */
  private int ok2;

  /**
   * Constructor.
   * @param nm name of test
   */
  public W3CTS(final String nm) {
    input = nm + "Catalog" + IO.XMLSUFFIX;
    testid = nm.substring(0, 4);
    pathhis = testid.toLowerCase() + ".hist";
    pathlog = testid.toLowerCase() + ".log";
  }

  /**
   * Runs the test suite.
   * @param args command-line arguments
   * @throws Exception exception
   */
  void run(final String[] args) throws Exception {
    final Args arg = new Args(args, this,
        " Test Suite [options] [pat]" + NL +
        " [pat] perform only tests with the specified pattern" + NL +
        " -c print compilation steps" + NL +
        " -d show debugging info" + NL +
        " -h show this help" + NL +
        " -m minimum conformance" + NL +
        " -p change path" + NL +
        " -r create report" + NL +
        " -v verbose output");

    while(arg.more()) {
      if(arg.dash()) {
        final char c = arg.next();
        if(c == 'r') {
          reporting = true;
          currTime = true;
        } else if(c == 'c') {
          compile = true;
        } else if(c == 'd') {
          debug = true;
        } else if(c == 'm') {
          minimum = true;
        } else if(c == 'p') {
          path = arg.string() + "/";
        } else if(c == 'v') {
          verbose = true;
        } else {
          arg.check(false);
        }
      } else {
        single = arg.string();
        maxout = Integer.MAX_VALUE;
      }
    }
    if(!arg.finish()) return;

    queries = path + "Queries/XQuery/";
    expected = path + "ExpectedTestResults/";
    results = path + "ReportingResults/Results/";
    report = path + "ReportingResults/";
    sources = path + "TestSources/";

    final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    final String dat = sdf.format(Calendar.getInstance().getTime());

    final Performance perf = new Performance();
    context.prop.set(Prop.MAINMEM, false);
    context.prop.set(Prop.TABLEMEM, false);
    context.prop.set(Prop.CHOP, false);

    new Check(path + input).execute(context);
    data = context.data;

    final Nodes root = new Nodes(0, data);
    Main.outln(NL + Main.name(this) + " Test Suite " +
        text("/*:test-suite/@version", root));

    Main.outln(NL + "Caching Sources...");
    for(final int s : nodes("//*:source", root).nodes) {
      final Nodes srcRoot = new Nodes(s, data);
      final String val = (path + text("@FileName", srcRoot)).replace('\\', '/');
      srcs.put(text("@ID", srcRoot), val);
    }

    Main.outln("Caching Modules...");
    for(final int s : nodes("//*:module", root).nodes) {
      final Nodes srcRoot = new Nodes(s, data);
      final String val = (path + text("@FileName", srcRoot)).replace('\\', '/');
      mods.put(text("@ID", srcRoot), val);
    }

    Main.outln("Caching Collections...");
    for(final int c : nodes("//*:collection", root).nodes) {
      final Nodes nodes = new Nodes(c, data);
      final String cname = text("@ID", nodes);

      final TokenList dl = new TokenList();
      final Nodes doc = nodes("*:input-document", nodes);
      for(int d = 0; d < doc.size(); ++d) {
        dl.add(token(sources + string(data.atom(doc.nodes[d])) + IO.XMLSUFFIX));
      }
      colls.put(cname, dl.toArray());
    }
    init(root);

    if(reporting) {
      Main.outln("Delete old results...");
      delete(new File[] { new File(results) });
    }

    if(verbose) Main.outln();
    final Nodes nodes = minimum ?
      nodes("//*:test-group[starts-with(@name, 'Minim')]//*:test-case", root) :
      nodes("//*:test-case", root);

    long total = nodes.size();
    Main.out("Parsing " + total + " Queries");
    for(int t = 0; t < total; ++t) {
      if(!parse(new Nodes(nodes.nodes[t], data))) break;
      if(!verbose && t % 500 == 0) Main.out(".");
    }
    Main.outln();
    total = ok + ok2 + err + err2;

    final String time = perf.getTimer();
    Main.outln("Writing log file..." + NL);
    BufferedWriter bw = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(path + pathlog), UTF8));
    bw.write("TEST RESULTS ==================================================");
    bw.write(NL + NL + "Total #Queries: " + total + NL);
    bw.write("Correct / Empty Results: " + ok + " / " + ok2 + NL);
    bw.write("Conformance (w/Empty Results): ");
    bw.write(pc(ok, total) + " / " + pc(ok + ok2, total) + NL);
    bw.write("Wrong Results / Errors: " + err + " / " + err2 + NL);
    bw.write("WRONG =========================================================");
    bw.write(NL + NL + logErr + NL);
    bw.write("WRONG (ERRORS) ================================================");
    bw.write(NL + NL + logErr2 + NL);
    bw.write("CORRECT? (EMPTY) ==============================================");
    bw.write(NL + NL + logOK2 + NL);
    bw.write("CORRECT =======================================================");
    bw.write(NL + NL + logOK + NL);
    bw.write("===============================================================");
    bw.close();

    bw = new BufferedWriter(new FileWriter(path + pathhis, true));
    bw.write(dat + "\t" + ok + "\t" + ok2 + "\t" + err + "\t" + err2 + NL);
    bw.close();

    if(reporting) {
      bw = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(report + NAME + IO.XMLSUFFIX), UTF8));
      write(bw, report + NAME + "Pre" + IO.XMLSUFFIX);
      bw.write(logReport.toString());
      write(bw, report + NAME + "Pos" + IO.XMLSUFFIX);
      bw.close();
    }

    Main.outln("Total #Queries: " + total);
    Main.outln("Correct / Empty results: " + ok + " / " + ok2);
    Main.out("Conformance (w/empty results): ");
    Main.outln(pc(ok, total) + " / " + pc(ok + ok2, total));
    Main.outln("Total Time: " + time);

    context.close();
  }

  /**
   * Calculates the percentage of correct queries.
   * @param v value
   * @param t total value
   * @return percentage
   */
  private String pc(final int v, final long t) {
    return (t == 0 ? 100 : v * 10000 / t / 100d) + "%";
  }

  /**
   * Parses the specified test case.
   * @param root root node
   * @throws Exception exception
   * @return true if the query, specified by {@link #single}, was evaluated
   */
  private boolean parse(final Nodes root) throws Exception {
    final String pth = text("@FilePath", root);
    final String outname = text("@name", root);
    if(single != null && !outname.startsWith(single)) return true;

    Performance perf = new Performance();
    if(verbose) Main.out("- " + outname);

    boolean inspect = false;
    boolean correct = true;

    final Nodes nodes = states(root);
    for(int n = 0; n < nodes.size(); ++n) {
      final Nodes state = new Nodes(nodes.nodes[n], nodes.data);

      final String inname = text("*:query/@name", state);
      context.query = IO.get(queries + pth + inname + IO.XQSUFFIX);
      final String in = read(context.query);
      String er = null;
      ItemIter iter = null;
      boolean doc = true;

      final TokenBuilder files = new TokenBuilder();
      // limit result sizes to 1MB
      final CachedOutput co = new CachedOutput(1 << 19);

      final Nodes cont = nodes("*:contextItem", state);
      Nodes curr = null;
      if(cont.size() != 0) {
        final Data d = Check.check(context,
            srcs.get(string(data.atom(cont.nodes[0]))));
        curr = new Nodes(d.doc(), d);
        curr.doc = true;
      }

      context.prop.set(Prop.QUERYINFO, compile);
      final QueryProcessor xq = new QueryProcessor(in, curr, context);
      context.prop.set(Prop.QUERYINFO, false);

      try {
        files.add(file(nodes("*:input-file", state),
            nodes("*:input-file/@variable", state), xq, n == 0));
        files.add(file(nodes("*:defaultCollection", state),
            null, xq, n == 0));

        var(nodes("*:input-URI", state),
            nodes("*:input-URI/@variable", state), xq);
        eval(nodes("*:input-query/@name", state),
            nodes("*:input-query/@variable", state), pth, xq);

        parse(xq, state);

        for(final int p : nodes("*:module", root).nodes) {
          final String ns = text("@namespace", new Nodes(p, data));
          final String f = mods.get(string(data.atom(p))) + IO.XQSUFFIX;
          xq.module(ns, f);
        }

        // evaluate and serialize query
        final SerializerProp sp = new SerializerProp();
        sp.set(SerializerProp.S_INDENT, context.prop.is(Prop.CHOP) ?
            DataText.YES : DataText.NO);
        final XMLSerializer xml = new XMLSerializer(co, sp);

        iter = ItemIter.get(xq.iter());
        Item it;
        while((it = iter.next()) != null) {
          doc &= it.type == Type.DOC;
          it.serialize(xml);
        }
        xml.close();
      } catch(final Exception ex) {
        if(!(ex instanceof QueryException || ex instanceof IOException))
          ex.printStackTrace();
        er = ex.getMessage();
        if(er.startsWith(STOPPED)) er = er.substring(er.indexOf('\n') + 1);
        if(er.startsWith("[")) er = er.replaceAll("\\[(.*?)\\] (.*)", "$1 $2");
        // unexpected error - dump stack trace
      }

      // print compilation steps
      if(compile) {
        Main.errln("---------------------------------------------------------");
        Main.err(xq.info());
        Main.errln(in);
      }

      final Nodes outFiles = nodes("*:output-file/text()", state);
      final Nodes cmpFiles = nodes("*:output-file/@compare", state);
      boolean xml = false;
      boolean frag = false;

      final TokenList result = new TokenList();
      for(int o = 0; o < outFiles.size(); ++o) {
        final String resFile = string(data.atom(outFiles.nodes[o]));
        final IO exp = IO.get(expected + pth + resFile);
        result.add(read(exp));
        final byte[] type = data.atom(cmpFiles.nodes[o]);
        xml |= eq(type, XML);
        frag |= eq(type, FRAGMENT);
      }
      String expError = text("*:expected-error/text()", state);

      final StringBuilder log = new StringBuilder(pth + inname + IO.XQSUFFIX);
      if(files.size() != 0) {
        log.append(" [");
        log.append(files);
        log.append("]");
      }
      log.append(NL);

      /** Remove comments. */
      log.append(norm(in));
      log.append(NL);
      final String logStr = log.toString();
      // skip queries with variable results
      final boolean print = currTime || !logStr.contains("current-");

      boolean correctError = false;
      if(er != null && (outFiles.size() == 0 || !expError.isEmpty())) {
        expError = error(pth + outname, expError);
        final String code = er.substring(0, Math.min(8, er.length()));
        for(final String e : SLASH.split(expError)) {
          if(code.equals(e)) {
            correctError = true;
            break;
          }
        }
      }

      if(correctError) {
        if(print) {
          logOK.append(logStr);
          logOK.append("[Right] ");
          logOK.append(norm(er));
          logOK.append(NL);
          logOK.append(NL);
          addLog(pth, outname + ".log", er);
        }
        ok++;
      } else if(er == null) {
        int s = -1;
        final int rs = result.size();
        while(++s < rs) {
          inspect |= s < cmpFiles.nodes.length &&
            eq(data.atom(cmpFiles.nodes[s]), INSPECT);

          final byte[] res = result.get(s);
          if(res.length == co.size() && eq(res, co.toArray())) break;

          if(xml || frag) {
            iter.reset();

            String ri = string(res).trim();
            if(!doc) {
              if(ri.startsWith("<?xml")) ri = ri.replaceAll("^<.*?>\\s*", "");
              ri = "<X>" + ri + "</X>";
            }

            try {
              final Data rdata = CreateDB.xml(IO.get(ri), context);
              final ItemIter ir = new ItemIter();
              for(int pre = doc ? 0 : 2; pre < rdata.meta.size;) {
                ir.add(new DBNode(rdata, pre));
                pre += rdata.size(pre, rdata.kind(pre));
              }

              // [CG] XQuery: check if null reference is safe
              final boolean eq = FNSimple.deep(null, iter, ir);
              if(!eq && debug) {
                iter.reset();
                ir.reset();
                final XMLSerializer ser = new XMLSerializer(System.out);
                Item it;
                Main.outln(NL + "=== " + testid + " ===");
                while((it = ir.next()) != null) it.serialize(ser);
                Main.outln(NL + "=== " + NAME + " ===");
                while((it = iter.next()) != null) it.serialize(ser);
                Main.outln();
              }
              rdata.close();
              if(eq) break;
            } catch(final Throwable ex) {
              ex.printStackTrace();
            }
          }
        }
        if((rs > 0 || !expError.isEmpty()) && s == rs && !inspect) {
          if(print) {
            if(outFiles.size() == 0) result.add(error(pth + outname, expError));
            logErr.append(logStr);
            logErr.append("[" + testid + " ] ");
            logErr.append(norm(string(result.get(0))));
            logErr.append(NL);
            logErr.append("[Wrong] ");
            logErr.append(norm(co.toString()));
            logErr.append(NL);
            logErr.append(NL);
            addLog(pth, outname + (xml ? IO.XMLSUFFIX : ".txt"),
                co.toString());
          }
          correct = false;
          err++;
        } else {
          if(print) {
            logOK.append(logStr);
            logOK.append("[Right] ");
            logOK.append(norm(co.toString()));
            logOK.append(NL);
            logOK.append(NL);
            addLog(pth, outname + (xml ? IO.XMLSUFFIX : ".txt"),
                co.toString());
          }
          ok++;
        }
      } else {
        if(outFiles.size() == 0 || !expError.isEmpty()) {
          if(print) {
            logOK2.append(logStr);
            logOK2.append("[" + testid + " ] ");
            logOK2.append(norm(expError));
            logOK2.append(NL);
            logOK2.append("[Rght?] ");
            logOK2.append(norm(er));
            logOK2.append(NL);
            logOK2.append(NL);
            addLog(pth, outname + ".log", er);
          }
          ok2++;
        } else {
          if(print) {
            logErr2.append(logStr);
            logErr2.append("[" + testid + " ] ");
            logErr2.append(norm(string(result.get(0))));
            logErr2.append(NL);
            logErr2.append("[Wrong] ");
            logErr2.append(norm(er));
            logErr2.append(NL);
            logErr2.append(NL);
            addLog(pth, outname + ".log", er);
          }
          correct = false;
          err2++;
        }
      }
      if(curr != null) Close.close(context, curr.data);
      xq.close();
    }

    if(reporting) {
      logReport.append("    <test-case name=\"");
      logReport.append(outname);
      logReport.append("\" result='");
      logReport.append(correct ? "pass" : "fail");
      if(inspect) logReport.append("' todo='inspect");
      logReport.append("'/>");
      logReport.append(NL);
    }

    if(verbose) {
      final long t = perf.getTime();
      if(t > 100000000) Main.out(": " + Performance.getTimer(t, 1));
      Main.outln();
    }
    return single == null || !outname.equals(single);
  }

  /**
   * Normalizes the specified string.
   * @param in input string
   * @return result
   */
  private String norm(final String in) {
    final StringBuilder sb = new StringBuilder();
    int m = 0;
    boolean s = false;
    final int cl = in.length();
    for(int c = 0; c < cl && sb.length() < maxout; ++c) {
      final char ch = in.charAt(c);
      if(ch == '(' && c + 1 < cl && in.charAt(c + 1) == ':') {
        if(m == 0 && !s) {
          sb.append(' ');
          s = true;
        }
        m++;
        c++;
      } else if(m != 0 && ch == ':' && c + 1 < cl && in.charAt(c + 1) == ')') {
        m--;
        c++;
      } else if(m == 0) {
        if(!s || ch > ' ') sb.append(ch);
        s = ch <= ' ';
      }
    }
    if(sb.length() >= maxout) sb.append("...");
    return sb.toString().replaceAll("(\r|\n)+", " ").trim();
  }

  /**
   * Initializes the input files, specified by the context nodes.
   * @param nod variables
   * @param var documents
   * @param qp query processor
   * @param first call
   * @return string with input files
   * @throws QueryException query exception
   */
  private byte[] file(final Nodes nod, final Nodes var,
      final QueryProcessor qp, final boolean first) throws QueryException {

    final TokenBuilder tb = new TokenBuilder();
    for(int c = 0; c < nod.size(); ++c) {
      final byte[] nm = data.atom(nod.nodes[c]);
      String src = srcs.get(string(nm));
      if(tb.size() != 0) tb.add(", ");
      tb.add(nm);

      Expr expr = null;
      if(src == null) {
        expr = coll(nm, qp);
      } else {
        // assign document
        FunDef def = FunDef.DOC;
        if(!first) {
          def = FunDef.DB;
          src = IO.get(src).dbname();
        }
        // [CG] XQuery/Query Info
        expr = def.newInstance(null, Str.get(src));
      }
      if(var != null) qp.bind(string(data.atom(var.nodes[c])), expr);
    }
    return tb.finish();
  }

  /**
   * Assigns the nodes to the specified variables.
   * @param nod nodes
   * @param var variables
   * @param qp query processor
   * @throws QueryException query exception
   */
  private void var(final Nodes nod, final Nodes var, final QueryProcessor qp)
      throws QueryException {

    for(int c = 0; c < nod.size(); ++c) {
      final byte[] nm = data.atom(nod.nodes[c]);
      final String src = srcs.get(string(nm));

      Expr expr = null;
      if(src == null) {
        expr = coll(nm, qp);
      } else {
        expr = Str.get(src);
      }
      qp.bind(string(data.atom(var.nodes[c])), expr);
    }
  }

  /**
   * Assigns a collection.
   * @param name collection name
   * @param qp query processor
   * @return expression
   * @throws QueryException query exception
   */
  private Expr coll(final byte[] name, final QueryProcessor qp)
      throws QueryException {

    // assign collection
    final NodIter col = new NodIter();
    for(final byte[] cl : colls.get(string(name))) {
      if(new File(string(cl)).exists()) {
        col.add(qp.ctx.doc(cl, true, false, null));
      } else {
        Main.errln("Warning: \"%\" not found.", cl);
      }
    }
    qp.ctx.addColl(col, name);
    return Uri.uri(name);
  }

  /**
   * Evaluates the the input files and assigns the result to the specified
   * variables.
   * @param nod variables
   * @param var documents
   * @param pth file path
   * @param qp query processor
   * @throws Exception exception
   */
  private void eval(final Nodes nod, final Nodes var, final String pth,
      final QueryProcessor qp) throws Exception {

    for(int c = 0; c < nod.size(); ++c) {
      final String file = pth + string(data.atom(nod.nodes[c])) + IO.XQSUFFIX;
      final String in = read(IO.get(queries + file));
      final QueryProcessor xq = new QueryProcessor(in, context);
      final Value val = xq.iter().finish();
      qp.bind(string(data.atom(var.nodes[c])), val);
      xq.close();
    }
  }

  /**
   * Adds a log file.
   * @param pth file path
   * @param nm file name
   * @param msg message
   * @throws Exception exception
   */
  private void addLog(final String pth, final String nm, final String msg)
      throws Exception {

    if(reporting) {
      final File file = new File(results + pth);
      if(!file.exists()) file.mkdirs();
      final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(results + pth + nm), UTF8));
      bw.write(msg);
      bw.close();
    }
  }

  /**
   * Returns an error message.
   * @param nm test name
   * @param error XQTS error
   * @return error message
   */
  private String error(final String nm, final String error) {
    final String error2 = expected + nm + ".log";
    final IO file = IO.get(error2);
    return file.exists() ? error + "/" + read(file) : error;
  }

  /**
   * Returns the resulting query text (text node or attribute value).
   * @param qu query
   * @param root root node
   * @return attribute value
   * @throws Exception exception
   */
  protected String text(final String qu, final Nodes root) throws Exception {
    final Nodes n = nodes(qu, root);
    final TokenBuilder sb = new TokenBuilder();
    for(int i = 0; i < n.size(); ++i) {
      if(i != 0) sb.add('/');
      sb.add(data.atom(n.nodes[i]));
    }
    return sb.toString();
  }

  /**
   * Returns the resulting auxiliary uri in multiple strings.
   * @param role role
   * @param root root node
   * @return attribute value
   * @throws Exception exception
   */
  protected String[] aux(final String role, final Nodes root) throws Exception {
    return text("*:aux-URI[@role = '" + role + "']", root).split("/");
  }

  /**
   * Returns the resulting query nodes.
   * @param qu query
   * @param root root node
   * @return attribute value
   * @throws Exception exception
   */
  protected Nodes nodes(final String qu, final Nodes root) throws Exception {
    return new QueryProcessor(qu, root, context).queryNodes();
  }

  /**
   * Recursively deletes a directory.
   * @param pth deletion path
   */
  void delete(final File[] pth) {
    for(final File f : pth) {
      if(f.isDirectory()) delete(f.listFiles());
      f.delete();
    }
  }

  /**
   * Adds the specified file to the writer.
   * @param bw writer
   * @param f file path
   * @throws Exception exception
   */
  private void write(final BufferedWriter bw, final String f) throws Exception {
    final BufferedReader br = new BufferedReader(new
        InputStreamReader(new FileInputStream(f), UTF8));
    String line;
    while((line = br.readLine()) != null) {
      bw.write(line);
      bw.write(NL);
    }
    br.close();
  }

  /**
   * Returns the contents of the specified file.
   * @param f file to be read
   * @return content
   */
  private String read(final IO f) {
    try {
      return TextInput.content(f).toString().replaceAll("\r\n?", "\n");
    } catch(final IOException ex) {
      ex.printStackTrace();
      return "";
    }
  }

  /**
   * Initializes the test.
   * @param root root nodes reference
   * @throws Exception exception
   */
  @SuppressWarnings("unused")
  void init(final Nodes root) throws Exception { }

  /**
   * Performs test specific parsings.
   * @param qp query processor
   * @param root root nodes reference
   * @throws Exception exception
   */
  @SuppressWarnings("unused")
  void parse(final QueryProcessor qp, final Nodes root) throws Exception { }

  /**
   * Returns all query states.
   * @param root root node
   * @return states
   * @throws Exception exception
   */
  @SuppressWarnings("unused")
  Nodes states(final Nodes root) throws Exception {
    return root;
  }
}
