<#import "template.ftl" as layout>
<@layout.emailLayout>
<@layout.heading>${msg("atelieEmailPasswordResetHeading")}</@layout.heading>
<@layout.p>${msg("atelieEmailPasswordResetIntro")}</@layout.p>
<@layout.button link msg("atelieEmailPasswordResetButton") />
<@layout.small>${msg("atelieLinkExpires", linkExpirationFormatter(linkExpiration))} ${msg("atelieEmailPasswordResetIgnore")}</@layout.small>
<@layout.fallbackLink link />
</@layout.emailLayout>
