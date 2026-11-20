<#import "template.ftl" as layout>
<@layout.emailLayout>
<@layout.heading>${msg("atelieEmailVerificationHeading")}</@layout.heading>
<@layout.p>${msg("atelieEmailVerificationIntro")}</@layout.p>
<@layout.button link msg("atelieEmailVerificationButton") />
<@layout.small>${msg("atelieLinkExpires", linkExpirationFormatter(linkExpiration))}</@layout.small>
<@layout.fallbackLink link />
</@layout.emailLayout>
