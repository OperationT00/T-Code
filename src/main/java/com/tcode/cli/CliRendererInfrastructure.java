package com.tcode.cli;

import com.tcode.hitl.RendererHitlHandler;
import com.tcode.hitl.SwitchableHitlHandler;
import com.tcode.render.Renderer;
import com.tcode.render.RendererFactory;
import com.tcode.render.StatusInfo;
import com.tcode.render.inline.InlineRenderer;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import java.io.PrintStream;

record CliRendererInfrastructure(Renderer renderer, PrintStream ui) {

    static CliRendererInfrastructure start(Terminal terminal,
                                           LineReader lineReader,
                                           SwitchableHitlHandler hitlHandler,
                                           StatusInfo initialStatus) {
        Renderer renderer = RendererFactory.create(RendererFactory.resolveMode(), terminal);
        return start(renderer, lineReader, hitlHandler, initialStatus);
    }

    static CliRendererInfrastructure start(Renderer renderer,
                                           LineReader lineReader,
                                           SwitchableHitlHandler hitlHandler,
                                           StatusInfo initialStatus) {
        hitlHandler.setDelegate(new RendererHitlHandler(renderer, hitlHandler.isEnabled()));
        if (renderer instanceof InlineRenderer inline) {
            inline.bindLineReader(lineReader);
        }
        PrintStream ui = renderer.stream();
        renderer.start();
        renderer.updateStatus(initialStatus);
        return new CliRendererInfrastructure(renderer, ui);
    }
}
