from jinja2 import Template
t = Template(template)
print t.render(
    # Here goes vars
    kate=False,
    kenny=None,
    lol="lalala"
)
