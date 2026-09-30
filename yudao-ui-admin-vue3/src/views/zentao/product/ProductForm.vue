<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="680">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="100px">
      <el-row>
        <el-col :span="14">
          <el-form-item label="产品名称" prop="name">
            <el-input v-model="formData.name" placeholder="请输入产品名称" maxlength="110" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="产品代号" prop="code">
            <el-input v-model="formData.code" placeholder="如 ZENTAO" maxlength="45" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="类型" prop="type">
            <el-select v-model="formData.type" class="w-full">
              <el-option v-for="o in PRODUCT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="访问控制" prop="acl">
            <el-select v-model="formData.acl" class="w-full">
              <el-option v-for="o in PRODUCT_ACL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="8">
          <el-form-item label="产品经理" prop="PO">
            <el-input v-model="formData.PO" placeholder="账号" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="测试负责人" prop="QD">
            <el-input v-model="formData.QD" placeholder="账号" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="研发负责人" prop="RD">
            <el-input v-model="formData.RD" placeholder="账号" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="所属项目集" prop="program">
        <el-select v-model="formData.program" filterable clearable placeholder="不选 = 独立产品"
                   class="!w-280px">
          <el-option label="（独立产品）" :value="0" />
          <el-option v-for="p in programList" :key="p.id" :label="p.name!" :value="p.id!" />
        </el-select>
        <span class="ml-10px text-gray-500 text-12px">zt_product.program</span>
      </el-form-item>

      <el-form-item label="排序" prop="order">
        <el-input-number v-model="formData.order" :min="0" class="w-full" />
      </el-form-item>

      <el-form-item label="产品描述" prop="desc">
        <el-input v-model="formData.desc" type="textarea" :rows="4" placeholder="请输入产品描述" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ProductApi from '@/api/zentao/product'
import * as ProgramApi from '@/api/zentao/program'
import { PRODUCT_TYPE_OPTIONS, PRODUCT_ACL_OPTIONS } from './constants'

defineOptions({ name: 'ZentaoProductForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()

const formData = ref<ProductApi.ProductVO>({})

// 项目集下拉：产品靠 zt_product.program 归属项目集
const programList = ref<ProgramApi.ProgramVO[]>([])

const formRules = reactive({
  name: [{ required: true, message: '产品名称不能为空', trigger: 'blur' }]
})

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增产品' : '编辑产品'
  formType.value = type
  resetForm()
  try {
    programList.value = await ProgramApi.getProgramSimpleList()
  } catch {
    programList.value = []
  }
  if (id) {
    formLoading.value = true
    try {
      formData.value = await ProductApi.getProduct(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await ProductApi.createProduct(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await ProductApi.updateProduct(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    name: '',
    code: '',
    program: 0,
    type: 'normal',
    acl: 'open',
    PO: 'admin',
    QD: '',
    RD: '',
    order: 0,
    desc: ''
  }
  formRef.value?.resetFields()
}
</script>
