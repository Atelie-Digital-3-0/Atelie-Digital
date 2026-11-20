<#import "template.ftl" as layout>
<@layout.emailLayout>
<@layout.heading>${msg("atelieEmailIdpLinkHeading")}</@layout.heading>
<@layout.p>${msg("atelieEmailIdpLinkIntro", identityProviderDisplayName, identityProviderContext.username)}</@layout.p>
<@layout.button link msg("atelieEmailIdpLinkButton") />
<@layout.small>${msg("atelieLinkExpires", linkExpirationFormatter(linkExpiration))} ${msg("atelieEmailIdpLinkIgnore")}</@layout.small>
<@layout.fallbackLink link />
</@layout.emailLayout>
