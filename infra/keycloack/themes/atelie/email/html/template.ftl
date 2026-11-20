<#-- Layout base dos e-mails do Atelie Digital (tabelas + estilos inline para funcionar em Gmail/Outlook).
     A marcacao fica aqui e nos .ftl de cada e-mail; as mensagens (messages_*.properties) so trazem texto puro. -->
<#assign fontBody = "system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif">
<#assign fontTitle = "Georgia,'Times New Roman',serif">

<#macro emailLayout>
<!DOCTYPE html>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="color-scheme" content="light">
    <title>Ateliê Digital</title>
</head>
<body style="margin:0;padding:0;background-color:#f9f5f1;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" bgcolor="#f9f5f1" style="background-color:#f9f5f1;">
    <tr>
        <td align="center" style="padding:32px 16px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;">
                <#-- Cabecalho com a marca -->
                <tr>
                    <td align="center" bgcolor="#9e502b" style="background-color:#9e502b;border-radius:16px 16px 0 0;padding:28px 24px;">
                        <span style="font-family:${fontTitle};font-size:28px;font-weight:700;color:#f9f5f1;letter-spacing:0.5px;">Ateliê Digital</span>
                    </td>
                </tr>
                <#-- Conteudo -->
                <tr>
                    <td bgcolor="#ffffff" style="background-color:#ffffff;padding:32px 32px 24px 32px;font-family:${fontBody};font-size:16px;line-height:1.6;color:#1e1e1e;">
                        <#nested>
                    </td>
                </tr>
                <#-- Rodape -->
                <tr>
                    <td align="center" bgcolor="#ffffff" style="background-color:#ffffff;border-top:1px solid #efe3da;border-radius:0 0 16px 16px;padding:20px 32px 28px 32px;font-family:${fontBody};font-size:12px;line-height:1.5;color:#7a7a7a;">
                        ${msg("atelieEmailFooter")}<br>${msg("atelieEmailFooterIgnore")}
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>
</#macro>

<#macro heading>
<h2 style="margin:0 0 16px 0;font-family:${fontTitle};font-size:22px;line-height:1.3;color:#9e502b;"><#nested></h2>
</#macro>

<#macro p>
<p style="margin:0 0 16px 0;"><#nested></p>
</#macro>

<#macro small>
<p style="margin:0 0 8px 0;font-size:14px;color:#6b6b6b;"><#nested></p>
</#macro>

<#-- Botao "a prova de cliente de e-mail": a cor de fundo fica na celula, nao no link. -->
<#macro button href label>
<table role="presentation" align="center" cellpadding="0" cellspacing="0" border="0" style="margin:24px auto;">
    <tr>
        <td align="center" bgcolor="#9e502b" style="background-color:#9e502b;border-radius:999px;">
            <a href="${href}" style="display:inline-block;padding:12px 28px;font-family:${fontBody};font-size:16px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:999px;">${label}</a>
        </td>
    </tr>
</table>
</#macro>

<#-- Endereco por extenso, caso o botao nao funcione -->
<#macro fallbackLink href>
<p style="margin:0;font-size:14px;color:#6b6b6b;">${msg("atelieLinkFallback")}<br><a href="${href}" style="color:#9e502b;word-break:break-all;">${href}</a></p>
</#macro>
