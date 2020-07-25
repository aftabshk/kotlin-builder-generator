package com.github.affishaikh.kotlinbuildergenerator.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class Greet: AnAction() {

    @Override
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showMessageDialog(
            "Hello World!",
            "My first Plugin",
            Messages.getInformationIcon()
        );
    }
}