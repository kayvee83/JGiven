package com.tngtech.jgiven.report.html;

import static java.lang.String.format;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Date;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.tngtech.jgiven.impl.util.ResourceUtil;
import com.tngtech.jgiven.impl.util.Version;
import com.tngtech.jgiven.report.impl.CommonReportHelper;
import com.tngtech.jgiven.report.model.ReportModel;
import com.tngtech.jgiven.report.model.ReportModelVisitor;
import com.tngtech.jgiven.report.model.ReportStatistics;
import com.tngtech.jgiven.report.model.ScenarioModel;
import com.tngtech.jgiven.report.model.StatisticsCalculator;

public class ReportModelHtmlWriter extends ReportModelVisitor {
    protected final PrintWriter writer;
    protected final HtmlWriterUtils utils;
    private ReportStatistics statistics;

    public ReportModelHtmlWriter( PrintWriter writer ) {
        this.writer = writer;
        utils = new HtmlWriterUtils( writer );
    }

    public void writeHtmlHeader( String title ) {
        utils.writeHtmlHeader( title );
        writer.write( "<div id='page'>" );
    }

    public void writeHtmlFooter() {
        writer.println( "</div> <!-- col-container -->" );
        writer.println( "<div id='page-footer'></div>" );
        writer.println( "</div> <!-- page -->" );
        writeJGivenFooter();
        writer.println( "<script src='report.js'></script>" );
        writer.println( "</body></html>" );
    }

    private void writeJGivenFooter() {
        writer.print( "<div id='footer'>Generated by <a href='http://jgiven.org'>JGiven</a> " );
        writer.print( Version.VERSION );
        writer.print( " - on " + DateFormat.getDateTimeInstance().format( new Date() ) );
        closeDiv();
    }

    private void closeDiv() {
        writer.println( "</div>" );
    }

    public void write( ScenarioModel model ) {
        writeHtmlHeader( model.className );
        model.accept( this );
        writeHtmlFooter();
    }

    public void write( ReportModel model, HtmlTocWriter htmlTocWriter ) {
        writeHtmlHeader( model.getClassName() );
        if( htmlTocWriter != null ) {
            htmlTocWriter.writeToc( writer );
        }
        model.accept( this );
        writeHtmlFooter();

    }

    private void writeStatistics( ReportModel model ) {
        if( !model.getScenarios().isEmpty() ) {
            statistics = new StatisticsCalculator().getStatistics( model );
            writer.print( "<div class='statistics'>" );
            writer.print( statistics.numScenarios + " scenarios, "
                    + statistics.numCases + " cases, "
                    + statistics.numSteps + " steps, "
                    + statistics.numFailedCases + " failed cases " );
            utils.writeDuration( statistics.durationInNanos );
            closeDiv();
        }
    }

    ReportStatistics getStatistics() {
        return statistics;
    }

    public static String toString( final ScenarioModel model ) {
        return toString( new Function<PrintWriter, Void>() {
            @Override
            public Void apply( PrintWriter input ) {
                new ReportModelHtmlWriter( input ).write( model );
                return null;
            }
        } );
    }

    public static String toString( final ReportModel model ) {
        return toString( new Function<PrintWriter, Void>() {
            @Override
            public Void apply( PrintWriter input ) {
                new ReportModelHtmlWriter( input ).write( model, null );
                return null;
            }
        } );
    }

    public static String toString( Function<PrintWriter, Void> writeFunction ) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter printWriter = null;

        try {
            printWriter = new PrintWriter( new OutputStreamWriter( stream, Charsets.UTF_8.name() ), false );
            writeFunction.apply( printWriter );
            printWriter.flush();
            return stream.toString( Charsets.UTF_8.name() );
        } catch( UnsupportedEncodingException e ) {
            throw Throwables.propagate( e );
        } finally {
            ResourceUtil.close( printWriter );
        }
    }

    public static void writeToFile( File file, ReportModel model, HtmlTocWriter htmlTocWriter ) throws FileNotFoundException,
            UnsupportedEncodingException {
        PrintWriter printWriter = new PrintWriter( file, Charsets.UTF_8.name() );
        try {
            new ReportModelHtmlWriter( printWriter ).write( model, htmlTocWriter );
            printWriter.flush();
        } finally {
            ResourceUtil.close( printWriter );
        }
    }

    @Override
    public void visit( ReportModel reportModel ) {
        writer.println( "<div id='rightpane'>" );
        writeHeader( reportModel );
        writer.println( "<div id='content'>" );
        if( !reportModel.getScenarios().isEmpty() ) {
            writer.println( "<input class='search-input' id='content-search-input' size='30'"
                    + " placeholder='enter regexp to search in scenarios'"
                    + " onkeydown='contentSearchChanged(event)'></input>" );
        }
    }

    void writeHeader( ReportModel reportModel ) {
        writer.println( "<div id='header'>" );
        writer.println( "<div id='header-title'>" );
        writer.println( "<i id='show-menu-icon' class='icon-menu collapsed' onclick='showToc()'></i>" );

        String packageName = "";
        String className = reportModel.getClassName();
        if( reportModel.getClassName().contains( "." ) ) {
            packageName = Files.getNameWithoutExtension( reportModel.getClassName() );
            className = Files.getFileExtension( reportModel.getClassName() );
        }

        if( !Strings.isNullOrEmpty( packageName ) ) {
            writer.println( format( "<div class='packagename'>%s</div>", packageName ) );
        }

        writer.println( format( "<h2>%s</h2>", className ) );
        writer.println( "</div> <!-- #header-title -->" );
        if( !Strings.isNullOrEmpty( reportModel.getDescription() ) ) {
            writer.println( format( "<div class='description'>%s</div>", reportModel.getDescription() ) );
        }

        closeDiv();
    }

    @Override
    public void visitEnd( ReportModel reportModel ) {
        writeStatistics( reportModel );

        writer.println( "</div> <!-- testcase-content -->" );
        writer.println( "</div> <!-- testcase -->" );
    }

    @Override
    public void visit( ScenarioModel scenarioModel ) {
        ScenarioHtmlWriter scenarioHtmlWriter;
        if( scenarioModel.isCasesAsTable() ) {
            scenarioHtmlWriter = new DataTableScenarioHtmlWriter( writer );
        } else {
            scenarioHtmlWriter = new MultiCaseScenarioHtmlWriter( writer );
        }
        scenarioModel.accept( scenarioHtmlWriter );
    }

    static ReportModelHtmlWriter writeModelToFile( ReportModel model, HtmlTocWriter tocWriter, File file ) {
        PrintWriter printWriter = CommonReportHelper.getPrintWriter( file );
        try {
            ReportModelHtmlWriter htmlWriter = new ReportModelHtmlWriter( printWriter );
            htmlWriter.write( model, tocWriter );
            return htmlWriter;
        } finally {
            ResourceUtil.close( printWriter );
        }

    }

}
