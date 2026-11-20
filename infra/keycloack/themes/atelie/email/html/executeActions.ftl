<#outputformat "plainText">
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#sep></#items></#list></#if></#assign>
</#outputformat>

<#import "template.ftl" as layout>
<@layout.emailLayout>
<@layout.heading>${msg("atelieEmailExecuteActionsHeading")}</@layout.heading>
<@layout.p>${msg("atelieEmailExecuteActionsIntro", requiredActionsText)}</@layout.p>
<@layout.button link msg("atelieEmailExecuteActionsButton") />
<@layout.small>${msg("atelieLinkExpires", linkExpirationFormatter(linkExpiration))} ${msg("atelieEmailExecuteActionsIgnore")}</@layout.small>
<@layout.fallbackLink link />
</@layout.emailLayout>
